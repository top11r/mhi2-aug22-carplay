// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 top11r
/*
 * libcarplay_tel.so - CarPlay call actions, recents and favourites for the K3346
 * cockpit.
 *
 * Preloaded into mm-ipod next to librgd_hook.so (the mm-ipod shim). It
 *  1. waits for mm-ipod to load the iAP2 driver (ipod-drvr-iap2.so) and
 *     replaces its Telephony group (90 slots) by a 0x72-slot copy - only if the
 *     group is exactly the stock one - with send slots InitiateCall 0x415A,
 *     AcceptCall 0x415B, EndCall 0x415C, HoldStatusUpdate 0x415F,
 *     MuteStatusUpdate 0x4160 and StartListUpdates 0x4170 (the driver declares
 *     every send slot in the Identify) and a receive slot ListUpdate 0x4171
 *     (without it the driver rejects the iPhone's lists and the link stalls);
 *  2. reads commands the HMI jar writes to the PPS object /ramdisk/pps/carplay/tel
 *     ("cmd::accept|end|hold|resume|mute|unmute|dial", "num::<number>",
 *     "ts::<ms>") and sends them with librgd_hook's harman_send_message(),
 *     which queues them to the driver's own thread. A command older than 3 s is
 *     dropped, never sent late;
 *  3. registers a module with librgd_hook's hook framework: when stock sends
 *     StartCallStateUpdates it asks for the lists once per link, and it turns
 *     every ListUpdate into the sticky bus text chefranov's jar reads
 *     (EVT_TEL_LISTS), which shows them on the cockpit.
 * Wire formats from luka-dev/mib2q-carplay-rgi (IAP2_COMMUNICATIONS.md,
 * tel_hook.c, GPL-3.0): Action 0 as parameter 0, no CallUUID, so iOS takes the
 * incoming / frontmost call; the list request and entries as his hook.
 */
#include <dlfcn.h>
#include <fcntl.h>
#include <pthread.h>
#include <stdarg.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <sys/time.h>
#include <time.h>
#include <unistd.h>

#include "carplay_tel.h"

static const char *const STOCK_NAMES[6] = {
    "StartTelephonyCallStateInformation", "TelephonyCallStateInformation",
    "StopTelephonyCallStateInformation", "StartTelephonyUpdates",
    "TelephonyUpdate", "StopTelephonyUpdates",
};
static const uint32_t STOCK_FLAGS[6] = {1, 2, 1, 1, 2, 1};

static struct tel_slot patched[TEL_NEW_SLOTS];

int tel_patch_table(struct tel_group *table)
{
    struct tel_group *g = &table[TEL_GROUP];
    int i;

    if (g->slots == patched && g->count == TEL_NEW_SLOTS)
        return 1;
    if (g->name == NULL || strcmp(g->name, "Telephony") != 0 || g->count != TEL_STOCK_SLOTS || g->slots == NULL)
        return -1;
    for (i = 0; i < 6; i++) {
        const struct tel_slot *s = &g->slots[0x54 + i];
        if (s->name == NULL || strcmp(s->name, STOCK_NAMES[i]) != 0 || s->flags != STOCK_FLAGS[i])
            return -1;
    }
    memcpy(patched, g->slots, TEL_STOCK_SLOTS * sizeof patched[0]);
    memset(&patched[TEL_STOCK_SLOTS], 0, (TEL_NEW_SLOTS - TEL_STOCK_SLOTS) * sizeof patched[0]);
    patched[TEL_MSG_ACCEPT & 0xff].name = "AcceptCall";
    patched[TEL_MSG_ACCEPT & 0xff].flags = TEL_SLOT_SEND;
    patched[TEL_MSG_END & 0xff].name = "EndCall";
    patched[TEL_MSG_END & 0xff].flags = TEL_SLOT_SEND;
    patched[TEL_MSG_HOLD & 0xff].name = "HoldStatusUpdate";
    patched[TEL_MSG_HOLD & 0xff].flags = TEL_SLOT_SEND;
    patched[TEL_MSG_MUTE & 0xff].name = "MuteStatusUpdate";
    patched[TEL_MSG_MUTE & 0xff].flags = TEL_SLOT_SEND;
    patched[TEL_MSG_DIAL & 0xff].name = "InitiateCall";
    patched[TEL_MSG_DIAL & 0xff].flags = TEL_SLOT_SEND;
    patched[TEL_MSG_START_LISTS & 0xff].name = "StartListUpdates";
    patched[TEL_MSG_START_LISTS & 0xff].flags = TEL_SLOT_SEND;
    /* The driver's link_handle_reply logs "Invalid packet" for a received id
     * past the group's count or without a name (the rc12 link stall); a named
     * slot without the reply flag is accepted ("Not a reply"). */
    patched[TEL_MSG_LIST_UPDATE & 0xff].name = "ListUpdate";
    patched[TEL_MSG_LIST_UPDATE & 0xff].flags = TEL_SLOT_RECV;
    /* slots before count: the driver reads count, then slots */
    g->slots = patched;
    __sync_synchronize();
    g->count = TEL_NEW_SLOTS;
    return 0;
}

int tel_parse(const char *text, char *cmd, size_t cmd_len, char *num, size_t num_len, long long *ts)
{
    const char *p = text;
    int have_cmd = 0, have_ts = 0;

    if (num_len)
        num[0] = 0;
    while (p && *p) {
        const char *end = strchr(p, '\n');
        size_t n = end ? (size_t)(end - p) : strlen(p);
        const char *c1 = memchr(p, ':', n);
        const char *c2 = c1 ? memchr(c1 + 1, ':', n - (size_t)(c1 + 1 - p)) : NULL;
        if (c2) {
            size_t klen = (size_t)(c1 - p), vlen = n - (size_t)(c2 + 1 - p);
            const char *v = c2 + 1;
            if (klen == 3 && memcmp(p, "cmd", 3) == 0 && vlen > 0 && vlen < cmd_len) {
                memcpy(cmd, v, vlen);
                cmd[vlen] = 0;
                have_cmd = 1;
            } else if (klen == 3 && memcmp(p, "num", 3) == 0 && vlen < num_len) {
                memcpy(num, v, vlen);
                num[vlen] = 0;
            } else if (klen == 2 && memcmp(p, "ts", 2) == 0) {
                char buf[24];
                if (vlen > 0 && vlen < sizeof buf) {
                    memcpy(buf, v, vlen);
                    buf[vlen] = 0;
                    *ts = strtoll(buf, NULL, 10);
                    have_ts = 1;
                }
            }
        }
        p = end ? end + 1 : NULL;
    }
    return have_cmd && have_ts ? 0 : -1;
}

int tel_command(const char *cmd, int *msgid, unsigned char *value)
{
    static const struct { const char *name; int msgid; unsigned char value; } CMDS[] = {
        {"accept", TEL_MSG_ACCEPT, 0}, {"end", TEL_MSG_END, 0},
        {"hold", TEL_MSG_HOLD, 1}, {"resume", TEL_MSG_HOLD, 0},
        {"mute", TEL_MSG_MUTE, 1}, {"unmute", TEL_MSG_MUTE, 0},
    };
    size_t i;
    for (i = 0; i < sizeof CMDS / sizeof CMDS[0]; i++) {
        if (strcmp(cmd, CMDS[i].name) == 0) {
            *msgid = CMDS[i].msgid;
            *value = CMDS[i].value;
            return 0;
        }
    }
    return -1;
}

/* iAP2 parameter: u16 length (with this 4-byte header), u16 id, data. */
static size_t put_param(unsigned char *out, size_t max, size_t off, unsigned id, const void *v, size_t n)
{
    size_t len = 4 + n;
    if (off + len > max || len > 0xffff)
        return 0;
    out[off] = (unsigned char)(len >> 8);
    out[off + 1] = (unsigned char)len;
    out[off + 2] = (unsigned char)(id >> 8);
    out[off + 3] = (unsigned char)id;
    if (n)
        memcpy(out + off + 4, v, n);
    return off + len;
}

size_t tel_build_dial(const char *number, unsigned char *out, size_t max)
{
    unsigned char type = 0, service = 1; /* destination, phone */
    size_t off;
    if (!number || !*number)
        return 0;
    off = put_param(out, max, 0, 0, &type, 1);
    if (off)
        off = put_param(out, max, off, 1, number, strlen(number) + 1); /* UTF-8, NUL-terminated */
    if (off)
        off = put_param(out, max, off, 2, &service, 1);
    return off;
}

/* Luka's tel_build_start_list_updates (mib2q-carplay-rgi, GPL-3.0): the layout
 * of the request his and chefranov's hook sent on our car (rc12, 89 bytes):
 * 1 recents properties 0-9, 3 RecentsListMax, 4 Combine, 6 favourites
 * properties 0-5, 8 FavoritesListMax. */
size_t tel_build_start_lists(unsigned char *out, size_t max, unsigned list_max)
{
    unsigned char group[64], be[2] = {(unsigned char)(list_max >> 8), (unsigned char)list_max}, one = 1;
    size_t g = 0, off;
    unsigned i;
    for (i = 0; i <= 9; i++)
        if (!(g = put_param(group, sizeof group, g, i, NULL, 0)))
            return 0;
    off = put_param(out, max, 0, 1, group, g);
    if (off)
        off = put_param(out, max, off, 3, be, 2);
    if (off)
        off = put_param(out, max, off, 4, &one, 1);
    for (g = 0, i = 0; off && i <= 5; i++)
        if (!(g = put_param(group, sizeof group, g, i, NULL, 0)))
            return 0;
    if (off)
        off = put_param(out, max, off, 6, group, g);
    if (off)
        off = put_param(out, max, off, 8, be, 2);
    return off;
}

/* Next parameter at p[0..len): id, value, value length; its full length, or 0. */
static size_t next_param(const unsigned char *p, size_t len, unsigned *id, const unsigned char **v, size_t *vlen)
{
    size_t plen;
    if (len < 4)
        return 0;
    plen = (size_t)p[0] << 8 | p[1];
    if (plen < 4 || plen > len)
        return 0;
    *id = (unsigned)p[2] << 8 | p[3];
    *v = p + 4;
    *vlen = plen - 4;
    return plen;
}

static unsigned long long be_n(const unsigned char *p, size_t n)
{
    unsigned long long v = 0;
    while (n--)
        v = v << 8 | *p++;
    return v;
}

/* One text value per bus line: control characters become spaces. */
static void copy_text(char *dst, const unsigned char *v, size_t n)
{
    size_t i;
    if (n >= TEL_TEXT_MAX)
        n = TEL_TEXT_MAX - 1;
    for (i = 0; i < n && v[i]; i++)
        dst[i] = v[i] < 0x20 ? ' ' : (char)v[i];
    dst[i] = 0;
}

/* Entry parameters (Luka, IAP2_COMMUNICATIONS.md): 0 index u16, 1 RemoteID,
 * 2 DisplayName, 6 type u8, 7 timestamp u64, 9 occurrences u8. */
static int parse_entry(const unsigned char *p, size_t len, struct tel_entry *e)
{
    size_t off = 0, step, n;
    const unsigned char *v;
    unsigned id;
    memset(e, 0, sizeof *e);
    while (off < len && (step = next_param(p + off, len - off, &id, &v, &n)) != 0) {
        if (id == 0 && n == 2)
            e->index = (unsigned)be_n(v, 2);
        else if (id == 1)
            copy_text(e->a, v, n);
        else if (id == 2)
            copy_text(e->b, v, n);
        else if (id == 6 && n == 1)
            e->type = v[0];
        else if (id == 7 && n == 8)
            e->ts = be_n(v, 8);
        else if (id == 9 && n == 1)
            e->occ = v[0];
        off += step;
    }
    return off == len ? 0 : -1;
}

int tel_apply_list_update(struct tel_lists *lists, const unsigned char *payload, size_t len)
{
    /* Parse fully first: a malformed update changes nothing. Static, not on the
     * stack: this runs on the iAP2 driver's receive thread, under lists_lock. */
    static struct tel_entry entries[2 * TEL_LIST_MAX];
    static unsigned which[2 * TEL_LIST_MAX];
    unsigned counts[2] = {0, 0}, have_count[2] = {0, 0}, n_entries = 0, i;
    size_t off = 0, step, n;
    const unsigned char *v;
    unsigned id;

    if (!payload)
        return -1;
    while (off < len && (step = next_param(payload + off, len - off, &id, &v, &n)) != 0) {
        unsigned list = id >= 5; /* 0-2 recents, 5-7 favourites */
        unsigned what = id - (list ? 5 : 0);
        if (what == 1) {
            if (n_entries < 2 * TEL_LIST_MAX && parse_entry(v, n, &entries[n_entries]) == 0)
                which[n_entries++] = list;
        } else if (what == 2 && n == 2) {
            counts[list] = (unsigned)be_n(v, 2);
            have_count[list] = 1;
        }
        off += step;
    }
    if (off != len)
        return -1;
    for (i = 0; i < 2; i++) {
        struct tel_list *l = i ? &lists->favorites : &lists->recents;
        if (have_count[i])
            l->n = counts[i] < TEL_LIST_MAX ? counts[i] : TEL_LIST_MAX;
    }
    for (i = 0; i < n_entries; i++) {
        struct tel_list *l = which[i] ? &lists->favorites : &lists->recents;
        unsigned at = entries[i].index;
        if (at >= TEL_LIST_MAX)
            continue;
        l->e[at] = entries[i];
        if (l->n <= at)
            l->n = at + 1;
    }
    return 0;
}

static size_t put_line(char *out, size_t max, size_t off, const char *fmt, ...)
{
    va_list ap;
    int n;
    if (off >= max)
        return 0;
    va_start(ap, fmt);
    n = vsnprintf(out + off, max - off, fmt, ap);
    va_end(ap);
    return n < 0 || (size_t)n >= max - off ? 0 : off + (size_t)n;
}

size_t tel_lists_text(const struct tel_lists *lists, char *out, size_t max)
{
    size_t off = put_line(out, max, 0, "@telephony\n");
    unsigned i, l;
    for (l = 0; off && l < 2; l++) {
        const struct tel_list *list = l ? &lists->favorites : &lists->recents;
        const char *p = l ? "f" : "r";
        off = put_line(out, max, off, "%s.n:n:%u\n", p, list->n);
        for (i = 0; off && i < list->n; i++) {
            const struct tel_entry *e = &list->e[i];
            off = put_line(out, max, off, "%s.%u.a:s:%s\n%s.%u.b:s:%s\n", p, i, e->a, p, i, e->b);
            if (off && !l)
                off = put_line(out, max, off, "r.%u.type:n:%u\nr.%u.ts:n:%llu\nr.%u.occ:n:%u\n",
                               i, e->type, i, e->ts, i, e->occ);
        }
    }
    return off;
}

#ifndef CARPLAY_TEL_HOST_TEST

#define LOG_PATH "/dev/shmem/carplay_tel.log"
#define LOG_MAX 65536
#define PPS_DIR "/ramdisk/pps/carplay"
#define PPS_OBJ PPS_DIR "/tel"
#define STALE_MS 3000

static const char *const DRIVER_PATHS[] = {
    "/mnt/app/armle/lib/dll/ipod-drvr-iap2.so", "/lib/dll/ipod-drvr-iap2.so",
    "/proc/boot/ipod-drvr-iap2.so", "ipod-drvr-iap2.so",
};

static long long now_ms(void)
{
    struct timeval tv;
    gettimeofday(&tv, NULL);
    return (long long)tv.tv_sec * 1000 + tv.tv_usec / 1000;
}

static void tel_log(const char *fmt, ...)
{
    FILE *f = fopen(LOG_PATH, "a");
    va_list ap;
    if (!f)
        return;
    fseek(f, 0, SEEK_END); /* append mode does not promise the position before a write */
    if (ftell(f) > LOG_MAX) {
        fclose(f);
        f = fopen(LOG_PATH, "w");
        if (!f)
            return;
    }
    fprintf(f, "%lld ", now_ms());
    va_start(ap, fmt);
    vfprintf(f, fmt, ap);
    va_end(ap);
    fputc('\n', f);
    fclose(f);
}

static void sleep_ms(long ms)
{
    struct timespec ts = {ms / 1000, (ms % 1000) * 1000000L};
    nanosleep(&ts, NULL);
}

/* mm-ipod dlopens its modules RTLD_GLOBAL (DL_DEBUG, emulator 2026-11-19), so
 * the table shows in the global scope once mm-ipod has loaded the driver.
 * QNX 6.5 ignores RTLD_NOLOAD: a dlopen here would load the driver before
 * mm-ipod does. So the global scope first, a dlopen only after 1 s and then
 * every 100 ms, as librgd_hook binds (harman_try_bind: RTLD_DEFAULT, then
 * dlopen). */
static struct tel_group *find_table(void)
{
    int i, tries;
    for (tries = 0; tries < 12000; tries++) { /* 60 s at 5 ms */
        void *t = dlsym(RTLD_DEFAULT, "iap2_ctrl_msg_table");
        if (t)
            return (struct tel_group *)t;
        if (tries >= 200 && tries % 20 == 0) {
            for (i = 0; i < (int)(sizeof DRIVER_PATHS / sizeof DRIVER_PATHS[0]); i++) {
                void *h = dlopen(DRIVER_PATHS[i], RTLD_NOW);
                if (h && (t = dlsym(h, "iap2_ctrl_msg_table")) != NULL)
                    return (struct tel_group *)t;
            }
        }
        sleep_ms(5);
    }
    return NULL;
}

typedef int (*send_fn)(int msgid, const void *payload, size_t len);
typedef int (*ready_fn)(void);

static void send_cmd(const char *cmd, const char *num, long long ts)
{
    /* One u8 as parameter 0 (length 5, id 0): Action 0, or the hold / mute
     * status - iOS reads only parameter 0 (Luka, IAP2_COMMUNICATIONS.md). */
    unsigned char payload[96] = {0x00, 0x05, 0x00, 0x00, 0x00};
    size_t len = 5;
    send_fn send = (send_fn)dlsym(RTLD_DEFAULT, "harman_send_message");
    ready_fn ready = (ready_fn)dlsym(RTLD_DEFAULT, "harman_link_ready");
    int msgid = -1;
    long long age = now_ms() - ts;
    int rc;

    if (strcmp(cmd, "dial") == 0) {
        msgid = TEL_MSG_DIAL;
        len = tel_build_dial(num, payload, sizeof payload);
        if (!len) {
            tel_log("cmd dial: no number, ignored");
            return;
        }
    } else if (tel_command(cmd, &msgid, &payload[4]) != 0) {
        tel_log("cmd '%s': unknown, ignored", cmd);
        return;
    }
    if (age > STALE_MS || age < -STALE_MS) {
        tel_log("cmd %s: %lld ms old, dropped", cmd, age);
        return;
    }
    if (!send || !ready) {
        tel_log("cmd %s: librgd_hook not loaded (no harman_send_message), dropped", cmd);
        return;
    }
    if (!ready()) {
        tel_log("cmd %s: iAP2 link not ready, dropped", cmd);
        return;
    }
    rc = send(msgid, payload, len);
    tel_log("cmd %s: 0x%04X %s (rc %d, %lld ms after the press)", cmd, msgid, rc == 0 ? "queued" : "NOT sent", rc, age);
}

/* ---- Lists: a module of chefranov's hook framework (librgd_hook.so). ----
 * RE of his build (sha256 0be02a0e...1b62): a module is 9 words copied by
 * value; incoming frames {u16 msgid, u16 length, u32, payload, payload_len}
 * with ctx->direction 0; on_transport_send(ctx, msgid) for every outgoing
 * message; state 7 on IdentificationAccepted. Up to 8 modules, he registers 2. */
struct hook_frame {
    uint16_t msgid;
    uint16_t length;
    uint32_t reserved;
    const unsigned char *payload;
    size_t payload_len;
};
struct hook_module {
    const char *name;
    int priority;
    const uint16_t *filter;
    size_t filter_count;
    int (*on_message)(void *ctx, const struct hook_frame *frame);
    void *on_identify;
    void (*on_state)(void *ctx, int event, void *data);
    void (*on_transport_send)(void *ctx, uint16_t msgid);
    void *user;
};
#define HOOK_EVENT_IDENTIFY_ACCEPTED 7
#define BUS_EVT_TEL_LISTS 0x0040
#define BUS_FLAG_STICKY 0x01

static pthread_mutex_t lists_lock = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t lists_wake = PTHREAD_COND_INITIALIZER;
static struct tel_lists lists;
static int lists_requested, lists_request_due;

typedef int (*bus_send_fn)(uint16_t type, uint8_t flags, const uint8_t *payload, uint32_t len);

/* Under lists_lock. */
static void publish_lists(void)
{
    static char text[16384];
    bus_send_fn bus_send = (bus_send_fn)dlsym(RTLD_DEFAULT, "bus_send");
    size_t n = tel_lists_text(&lists, text, sizeof text);
    if (bus_send && n)
        bus_send(BUS_EVT_TEL_LISTS, BUS_FLAG_STICKY, (const uint8_t *)text, (uint32_t)n);
    tel_log("lists: recents=%u favorites=%u%s", lists.recents.n, lists.favorites.n, bus_send && n ? "" : " (not published)");
}

/* The link is up: ask once per link, from our own thread (never from inside
 * the driver's send or receive path). */
static void ask_lists_once(void)
{
    pthread_mutex_lock(&lists_lock);
    if (!lists_requested) {
        lists_requested = 1;
        lists_request_due = 1;
        pthread_cond_signal(&lists_wake);
    }
    pthread_mutex_unlock(&lists_lock);
}

static int on_message(void *ctx, const struct hook_frame *f)
{
    if (!ctx || *(const int *)ctx != 0)
        return 0;
    if (f->msgid == TEL_MSG_TEL_UPDATE) { /* car: on_transport_send is not called for stock's 0x4154 */
        ask_lists_once();
        return 0;
    }
    if (f->msgid != TEL_MSG_LIST_UPDATE)
        return 0;
    pthread_mutex_lock(&lists_lock);
    if (tel_apply_list_update(&lists, f->payload, f->payload_len) == 0)
        publish_lists();
    else
        tel_log("malformed ListUpdate (%u bytes) ignored", (unsigned)f->payload_len);
    pthread_mutex_unlock(&lists_lock);
    return 0; /* stock keeps every message */
}

/* Stock's StartCallStateUpdates leaves: the link is up. */
static void on_transport_send(void *ctx, uint16_t msgid)
{
    static int seen;
    (void)ctx;
    if (!seen) {
        seen = 1;
        tel_log("on_transport_send called (first 0x%04X)", msgid);
    }
    if (msgid == TEL_MSG_START_CALL_STATE)
        ask_lists_once();
}

static void on_state(void *ctx, int event, void *data)
{
    (void)ctx;
    (void)data;
    if (event != HOOK_EVENT_IDENTIFY_ACCEPTED)
        return;
    pthread_mutex_lock(&lists_lock); /* a new link: ask again, start empty */
    lists_requested = 0;
    memset(&lists, 0, sizeof lists);
    publish_lists();
    pthread_mutex_unlock(&lists_lock);
}

static void *request_lists(void *arg)
{
    unsigned char payload[128];
    send_fn send;
    size_t n = tel_build_start_lists(payload, sizeof payload, TEL_LIST_MAX);
    (void)arg;
    for (;;) {
        int rc;
        pthread_mutex_lock(&lists_lock);
        while (!lists_request_due)
            pthread_cond_wait(&lists_wake, &lists_lock);
        lists_request_due = 0;
        pthread_mutex_unlock(&lists_lock);
        send = (send_fn)dlsym(RTLD_DEFAULT, "harman_send_message");
        rc = send && n ? send(TEL_MSG_START_LISTS, payload, n) : -1;
        tel_log("StartListUpdates %s (%u bytes, rc %d)", rc == 0 ? "queued" : "NOT sent", (unsigned)n, rc);
        if (rc != 0) {
            pthread_mutex_lock(&lists_lock);
            lists_requested = 0; /* the next trigger retries */
            pthread_mutex_unlock(&lists_lock);
        }
    }
    return NULL;
}

typedef int (*register_fn)(const struct hook_module *def);

static void start_lists(void)
{
    static const uint16_t filter[] = {TEL_MSG_LIST_UPDATE, TEL_MSG_TEL_UPDATE};
    static const struct hook_module module = {
        "carplay_tel", 0x60, filter, 2, on_message, NULL, on_state, on_transport_send, NULL,
    };
    register_fn reg = (register_fn)dlsym(RTLD_DEFAULT, "hook_framework_register_module");
    pthread_t t;
    pthread_attr_t a;
    int rc;

    if (!reg) {
        tel_log("librgd_hook has no hook framework: lists off");
        return;
    }
    pthread_attr_init(&a);
    pthread_attr_setdetachstate(&a, PTHREAD_CREATE_DETACHED);
    if (pthread_create(&t, &a, request_lists, NULL) != 0) {
        pthread_attr_destroy(&a);
        tel_log("no list thread: lists off");
        return;
    }
    pthread_attr_destroy(&a);
    rc = reg(&module);
    tel_log("lists module %s (rc %d)", rc == 0 ? "registered" : "NOT registered", rc);
}

static void read_commands(void)
{
    char buf[512], cmd[16], num[48];
    long long last_ts = 0;
    int fd;

    mkdir(PPS_DIR, 0777);
    fd = open(PPS_OBJ, O_RDWR | O_CREAT, 0666);
    if (fd >= 0)
        close(fd);
    for (;;) {
        ssize_t n;
        fd = open(PPS_OBJ "?wait", O_RDONLY);
        if (fd < 0) {
            tel_log("open %s?wait failed; retrying", PPS_OBJ);
            sleep_ms(1000);
            continue;
        }
        while ((n = read(fd, buf, sizeof buf - 1)) > 0) {
            long long ts = 0;
            buf[n] = 0;
            if (tel_parse(buf, cmd, sizeof cmd, num, sizeof num, &ts) == 0 && ts != last_ts) {
                last_ts = ts;
                send_cmd(cmd, num, ts);
            }
        }
        close(fd);
        sleep_ms(500);
    }
}

static void *run(void *arg)
{
    struct tel_group *table;
    int rc;
    (void)arg;

    table = find_table();
    if (!table)
        return NULL; /* not mm-ipod, or no iAP2 driver: stay silent */
    rc = tel_patch_table(table);
    if (rc < 0) {
        tel_log("telephony group is not the stock one; nothing patched, call actions off");
        return NULL;
    }
    tel_log("InitiateCall / AcceptCall / EndCall / HoldStatusUpdate / MuteStatusUpdate / StartListUpdates declared, "
            "ListUpdate accepted (%s); reading %s", rc == 0 ? "patched" : "already patched", PPS_OBJ);
    start_lists();
    read_commands();
    return NULL;
}

#ifndef CARPLAY_TEL_NO_INIT /* test/module_test.c drives the module itself */
__attribute__((constructor)) static void carplay_tel_init(void)
{
    pthread_t t;
    pthread_attr_t a;
    pthread_attr_init(&a);
    pthread_attr_setdetachstate(&a, PTHREAD_CREATE_DETACHED);
    pthread_create(&t, &a, run, NULL);
    pthread_attr_destroy(&a);
}
#endif

#endif
