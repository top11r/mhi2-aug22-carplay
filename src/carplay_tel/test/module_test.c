// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 top11r
/* Host test of the glue around the lists and dial: the hook module's callbacks,
 * the request thread and send_cmd, with librgd_hook's exports stubbed here
 * (found by the library's dlsym(RTLD_DEFAULT), as on the unit).
 *   cc -DCARPLAY_TEL_NO_INIT -I.. module_test.c -o module_test -lpthread && ./module_test
 * (Linux: add -rdynamic so dlsym sees the stubs.) */
#include "../carplay_tel.c"

#define EXPORT __attribute__((visibility("default"), used))

static int failures;
static void check(const char *what, int ok)
{
    printf("%s %s\n", ok ? "ok  " : "FAIL", what);
    if (!ok)
        failures++;
}

static pthread_mutex_t seen_lock = PTHREAD_MUTEX_INITIALIZER;
static int sends, last_msgid, publishes, last_type, last_flags, registered;
static unsigned char last_payload[128];
static size_t last_len;
static char last_text[4096];
static struct hook_module module_copy;

EXPORT int harman_link_ready(void) { return 1; }

EXPORT int harman_send_message(int msgid, const void *payload, size_t len)
{
    pthread_mutex_lock(&seen_lock);
    sends++;
    last_msgid = msgid;
    last_len = len < sizeof last_payload ? len : sizeof last_payload;
    memcpy(last_payload, payload, last_len);
    pthread_mutex_unlock(&seen_lock);
    return 0;
}

EXPORT int bus_send(uint16_t type, uint8_t flags, const uint8_t *payload, uint32_t len)
{
    publishes++;
    last_type = type;
    last_flags = flags;
    len = len < sizeof last_text - 1 ? len : (uint32_t)sizeof last_text - 1;
    memcpy(last_text, payload, len);
    last_text[len] = 0;
    return 0;
}

EXPORT int hook_framework_register_module(const struct hook_module *def)
{
    module_copy = *def; /* the framework copies the 9 words */
    registered++;
    return 0;
}

static int sends_now(void)
{
    int n;
    pthread_mutex_lock(&seen_lock);
    n = sends;
    pthread_mutex_unlock(&seen_lock);
    return n;
}

/* The request goes out from the library's own thread: wait for it. */
static int wait_sends(int n)
{
    int i;
    for (i = 0; i < 200 && sends_now() < n; i++)
        sleep_ms(5);
    return sends_now();
}

int main(void)
{
    int incoming = 0, outgoing = 1;
    /* ListUpdate payload: recents count 1, one recents entry (index 0, number,
     * name, type 3 missed), favourites count 0. */
    static const unsigned char update[] = {
        0x00, 0x06, 0x00, 0x02, 0x00, 0x01,                                   /* recents count 1 */
        0x00, 0x24, 0x00, 0x01,                                               /* recents entry (36 bytes) */
        0x00, 0x06, 0x00, 0x00, 0x00, 0x00,                                   /*   index 0 */
        0x00, 0x0d, 0x00, 0x01, '+', '3', '1', '6', '1', '2', '3', '4', 0x00, /*   RemoteID */
        0x00, 0x08, 0x00, 0x02, 'A', 'n', 'n', 0x00,                          /*   DisplayName */
        0x00, 0x05, 0x00, 0x06, 0x03,                                         /*   type missed */
        0x00, 0x06, 0x00, 0x07, 0x00, 0x00,                                   /* favourites count 0 */
    };
    struct hook_frame frame = {TEL_MSG_LIST_UPDATE, 0, 0, update, sizeof update};

    (void)run; /* the constructor is compiled out */
    start_lists();
    check("module registered once", registered == 1);
    check("module: name, filter {0x4171, 0x4158}, callbacks", strcmp(module_copy.name, "carplay_tel") == 0
          && module_copy.filter_count == 2 && module_copy.filter[0] == TEL_MSG_LIST_UPDATE
          && module_copy.filter[1] == TEL_MSG_TEL_UPDATE
          && module_copy.on_message && module_copy.on_state && module_copy.on_transport_send
          && !module_copy.on_identify);

    module_copy.on_transport_send(&outgoing, 0x4157);
    check("other outgoing messages ask nothing", wait_sends(1) == 0);
    module_copy.on_transport_send(&outgoing, TEL_MSG_START_CALL_STATE);
    module_copy.on_transport_send(&outgoing, TEL_MSG_START_CALL_STATE);
    check("stock's 0x4154 -> one StartListUpdates", wait_sends(1) == 1 && last_msgid == TEL_MSG_START_LISTS && last_len == 89);
    check("a second 0x4154 asks nothing", wait_sends(2) == 1);

    module_copy.on_message(&outgoing, &frame);
    check("an outgoing 0x4171 is ignored", publishes == 0);
    module_copy.on_message(&incoming, &frame);
    check("ListUpdate -> one sticky EVT_TEL_LISTS", publishes == 1 && last_type == 0x0040 && last_flags == 1);
    check("published text as the jar reads it", strstr(last_text, "@telephony\nr.n:n:1\nr.0.a:s:+3161234\nr.0.b:s:Ann\nr.0.type:n:3\n") != NULL
          && strstr(last_text, "\nf.n:n:0\n") != NULL);
    frame.payload_len = 7;
    module_copy.on_message(&incoming, &frame);
    check("malformed ListUpdate publishes nothing", publishes == 1);

    module_copy.on_state(&incoming, 8, NULL);
    check("other state events change nothing", publishes == 1);
    module_copy.on_state(&incoming, HOOK_EVENT_IDENTIFY_ACCEPTED, NULL);
    check("new link: empty lists published", publishes == 2 && strstr(last_text, "r.n:n:0\nf.n:n:0\n") != NULL);
    module_copy.on_transport_send(&outgoing, TEL_MSG_START_CALL_STATE);
    check("new link: 0x4154 asks again", wait_sends(2) == 2 && last_msgid == TEL_MSG_START_LISTS);

    /* The car: no on_transport_send; the first incoming TelephonyUpdate asks. */
    module_copy.on_state(&incoming, HOOK_EVENT_IDENTIFY_ACCEPTED, NULL);
    frame.msgid = TEL_MSG_TEL_UPDATE;
    module_copy.on_message(&outgoing, &frame);
    check("an outgoing 0x4158 asks nothing", wait_sends(3) == 2);
    module_copy.on_message(&incoming, &frame);
    module_copy.on_message(&incoming, &frame);
    check("incoming 0x4158 -> one StartListUpdates", wait_sends(3) == 3 && last_msgid == TEL_MSG_START_LISTS);
    check("a second 0x4158 asks nothing", wait_sends(4) == 3);

    send_cmd("dial", "+3161234", now_ms());
    check("dial -> InitiateCall with the number", sends_now() == 4 && last_msgid == TEL_MSG_DIAL && last_len == 23
          && memcmp(last_payload + 9, "+3161234", 9) == 0);
    send_cmd("dial", "", now_ms());
    check("dial without a number sends nothing", sends_now() == 4);
    send_cmd("dial", "+3161234", now_ms() - 5000);
    check("stale dial dropped", sends_now() == 4);
    send_cmd("hold", "", now_ms());
    check("hold still 0x415F status 1", sends_now() == 5 && last_msgid == TEL_MSG_HOLD && last_len == 5 && last_payload[4] == 1);

    printf(failures ? "%d failed\n" : "all passed\n", failures);
    return failures != 0;
}
