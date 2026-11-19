// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 top11r
/* Host check of the table patch and the command parser:
 *   cc -DCARPLAY_TEL_HOST_TEST -I.. ../carplay_tel.c tel_test.c -o tel_test && ./tel_test */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "carplay_tel.h"

static int failures;
static void check(const char *what, int ok)
{
    printf("%s %s\n", ok ? "ok  " : "FAIL", what);
    if (!ok)
        failures++;
}

/* From luka-dev/mib2q-carplay-rgi tests/tel_hook_test.c (GPL-3.0): the
 * car-verified StartListUpdates (recents max 25, favourites max 0) and captured
 * ListUpdate frames, with their 6-byte 40 40 len 41 71 header. */
static const unsigned char verified_start[] =
    "\x00\x2c\x00\x01"
    "\x00\x04\x00\x00\x00\x04\x00\x01\x00\x04\x00\x02\x00\x04\x00\x03\x00\x04\x00\x04"
    "\x00\x04\x00\x05\x00\x04\x00\x06\x00\x04\x00\x07\x00\x04\x00\x08\x00\x04\x00\x09"
    "\x00\x06\x00\x03\x00\x19"
    "\x00\x05\x00\x04\x01"
    "\x00\x1c\x00\x06"
    "\x00\x04\x00\x00\x00\x04\x00\x01\x00\x04\x00\x02\x00\x04\x00\x03\x00\x04\x00\x04\x00\x04\x00\x05"
    "\x00\x06\x00\x08\x00\x00";
static const unsigned char cap_avail[] =
    "\x40\x40\x00\x11\x41\x71\x00\x05\x00\x00\x01\x00\x06\x00\x02\x00"
    "\x03";
static const unsigned char cap_entries[] =
    "\x40\x40\x01\x4b\x41\x71\x00\x7b\x00\x01\x00\x06\x00\x00\x00\x00"
    "\x00\x13\x00\x01\x2b\x38\x36\x31\x33\x38\x30\x30\x30\x30\x30\x30"
    "\x30\x31\x00\x00\x0f\x00\x02\x41\x6c\x69\x63\x65\x20\x54\x65\x73"
    "\x74\x00\x00\x0b\x00\x03\xe6\x89\x8b\xe6\x9c\xba\x00\x00\x29\x00"
    "\x04\x37\x44\x31\x39\x31\x37\x30\x41\x2d\x39\x35\x39\x43\x2d\x34"
    "\x32\x30\x34\x2d\x41\x43\x32\x41\x2d\x36\x42\x45\x33\x38\x36\x34"
    "\x44\x43\x43\x42\x43\x00\x00\x05\x00\x05\x03\x00\x05\x00\x06\x02"
    "\x00\x0c\x00\x07\x00\x00\x00\x00\x69\xec\x67\x54\x00\x05\x00\x09"
    "\x02\x00\x65\x00\x01\x00\x06\x00\x00\x00\x01\x00\x13\x00\x01\x2b"
    "\x38\x36\x31\x33\x38\x30\x30\x30\x30\x30\x30\x30\x32\x00\x00\x16"
    "\x00\x02\x2b\x38\x36\x20\x31\x33\x38\x20\x30\x30\x30\x30\x20\x30"
    "\x30\x30\x32\x00\x00\x12\x00\x03\xe6\xb5\x99\xe6\xb1\x9f\x20\xe6"
    "\x9d\xad\xe5\xb7\x9e\x00\x00\x05\x00\x04\x00\x00\x05\x00\x05\x03"
    "\x00\x05\x00\x06\x02\x00\x0c\x00\x07\x00\x00\x00\x00\x69\x45\x7b"
    "\x8f\x00\x05\x00\x09\x02\x00\x65\x00\x01\x00\x06\x00\x00\x00\x02"
    "\x00\x13\x00\x01\x2b\x38\x36\x31\x33\x38\x30\x30\x30\x30\x30\x30"
    "\x30\x32\x00\x00\x16\x00\x02\x2b\x38\x36\x20\x31\x33\x38\x20\x30"
    "\x30\x30\x30\x20\x30\x30\x30\x32\x00\x00\x12\x00\x03\xe6\xb5\x99"
    "\xe6\xb1\x9f\x20\xe6\x9d\xad\xe5\xb7\x9e\x00\x00\x05\x00\x04\x00"
    "\x00\x05\x00\x05\x00\x00\x05\x00\x06\x02\x00\x0c\x00\x07\x00\x00"
    "\x00\x00\x69\x45\x75\x38\x00\x05\x00\x09\x07";
static const unsigned char cap_fav[] =
    "\x40\x40\x00\x11\x41\x71\x00\x05\x00\x05\x01\x00\x06\x00\x07\x00"
    "\x00";

static struct tel_group table[256];
static struct tel_slot stock[TEL_STOCK_SLOTS];

static void stock_table(void)
{
    static const char *names[6] = {"StartTelephonyCallStateInformation", "TelephonyCallStateInformation",
        "StopTelephonyCallStateInformation", "StartTelephonyUpdates", "TelephonyUpdate", "StopTelephonyUpdates"};
    static const unsigned flags[6] = {1, 2, 1, 1, 2, 1};
    int i;
    memset(stock, 0, sizeof stock);
    for (i = 0; i < 6; i++) {
        stock[0x54 + i].name = names[i];
        stock[0x54 + i].flags = flags[i];
    }
    table[TEL_GROUP].name = "Telephony";
    table[TEL_GROUP].slots = stock;
    table[TEL_GROUP].count = TEL_STOCK_SLOTS;
}

int main(void)
{
    char cmd[16], num[48];
    long long ts = 0;
    int i, sends = 0;

    stock_table();
    check("stock group is patched", tel_patch_table(table) == 0);
    check("0x72 slots", table[TEL_GROUP].count == 0x72 && table[TEL_GROUP].slots != stock);
    check("0x415B send-only", table[TEL_GROUP].slots[0x5B].flags == TEL_SLOT_SEND
          && strcmp(table[TEL_GROUP].slots[0x5B].name, "AcceptCall") == 0);
    check("0x415C send-only", table[TEL_GROUP].slots[0x5C].flags == TEL_SLOT_SEND
          && strcmp(table[TEL_GROUP].slots[0x5C].name, "EndCall") == 0);
    check("0x415F / 0x4160 send-only", table[TEL_GROUP].slots[0x5F].flags == TEL_SLOT_SEND
          && table[TEL_GROUP].slots[0x60].flags == TEL_SLOT_SEND);
    check("0x415A InitiateCall / 0x4170 StartListUpdates send-only", table[TEL_GROUP].slots[0x5A].flags == TEL_SLOT_SEND
          && table[TEL_GROUP].slots[0x70].flags == TEL_SLOT_SEND && strcmp(table[TEL_GROUP].slots[0x70].name, "StartListUpdates") == 0);
    check("0x4171 ListUpdate received, named, no reply flag", table[TEL_GROUP].slots[0x71].flags == TEL_SLOT_RECV
          && strcmp(table[TEL_GROUP].slots[0x71].name, "ListUpdate") == 0);
    check("0x415D / 0x415E stay undeclared", table[TEL_GROUP].slots[0x5D].flags == 0 && table[TEL_GROUP].slots[0x5E].flags == 0);
    check("stock slots copied", table[TEL_GROUP].slots[0x55].flags == 2
          && strcmp(table[TEL_GROUP].slots[0x57].name, "StartTelephonyUpdates") == 0);
    /* what ident_info_sendmsgs declares: (flags & 0x21) == 1 */
    for (i = 0; i < table[TEL_GROUP].count; i++)
        if ((table[TEL_GROUP].slots[i].flags & 0x21) == 1)
            sends++;
    check("declared: 0x4154/56/57/59 + 0x415A/5B/5C/5F/60/70", sends == 10);
    check("second patch is a no-op", tel_patch_table(table) == 1);

    stock_table();
    stock[0x55].flags = 1;
    check("changed slot flags -> not patched", tel_patch_table(table) == -1 && table[TEL_GROUP].slots == stock);
    stock_table();
    table[TEL_GROUP].count = 91;
    check("other slot count -> not patched", tel_patch_table(table) == -1);
    stock_table();
    stock[0x59].name = "Something";
    check("other slot name -> not patched", tel_patch_table(table) == -1);

    check("parse accept", tel_parse("@tel\ncmd::accept\nts::1731960000123\n", cmd, sizeof cmd, num, sizeof num, &ts) == 0
          && strcmp(cmd, "accept") == 0 && ts == 1731960000123LL);
    check("parse end, any order", tel_parse("ts::42\ncmd::end", cmd, sizeof cmd, num, sizeof num, &ts) == 0
          && strcmp(cmd, "end") == 0 && ts == 42);
    check("no ts -> rejected", tel_parse("cmd::accept\n", cmd, sizeof cmd, num, sizeof num, &ts) == -1);
    check("too long cmd -> rejected", tel_parse("cmd::acceptacceptaccept\nts::1\n", cmd, sizeof cmd, num, sizeof num, &ts) == -1);
    {
        int id = 0;
        unsigned char v = 9;
        check("accept -> 0x415B action 0", tel_command("accept", &id, &v) == 0 && id == 0x415B && v == 0);
        check("end -> 0x415C action 0", tel_command("end", &id, &v) == 0 && id == 0x415C && v == 0);
        check("hold / resume -> 0x415F 1 / 0", tel_command("hold", &id, &v) == 0 && id == 0x415F && v == 1
              && tel_command("resume", &id, &v) == 0 && id == 0x415F && v == 0);
        check("mute / unmute -> 0x4160 1 / 0", tel_command("mute", &id, &v) == 0 && id == 0x4160 && v == 1
              && tel_command("unmute", &id, &v) == 0 && id == 0x4160 && v == 0);
        check("swap / dial not single-byte commands", tel_command("swap", &id, &v) == -1 && tel_command("dial", &id, &v) == -1);
    }
    check("parse dial with number", tel_parse("cmd::dial\nnum::+31612345678\nts::7\n", cmd, sizeof cmd, num, sizeof num, &ts) == 0
          && strcmp(cmd, "dial") == 0 && strcmp(num, "+31612345678") == 0 && ts == 7);
    check("no num -> empty", tel_parse("cmd::end\nts::7\n", cmd, sizeof cmd, num, sizeof num, &ts) == 0 && num[0] == 0);
    {
        unsigned char out[64];
        static const unsigned char want[] = {0x00, 0x05, 0x00, 0x00, 0x00,                              /* Type 0 destination */
            0x00, 0x0d, 0x00, 0x01, '+', '3', '1', '6', '1', '2', '3', '4', 0x00,                          /* DestinationID */
            0x00, 0x05, 0x00, 0x02, 0x01};                                                                 /* Service 1 phone */
        size_t n = tel_build_dial("+3161234", out, sizeof out);
        check("InitiateCall: type, number (NUL), service", n == sizeof want && memcmp(out, want, n) == 0);
        check("InitiateCall: empty number refused", tel_build_dial("", out, sizeof out) == 0 && tel_build_dial(NULL, out, sizeof out) == 0);
        check("InitiateCall: too small refused", tel_build_dial("+3161234", out, 10) == 0);
    }
    {
        unsigned char out[128];
        size_t n = tel_build_start_lists(out, sizeof out, 25);
        /* the verified request asks favourites max 0 (= the iOS default cap); ours the same max for both */
        check("StartListUpdates: 89 bytes as on the car (rc12)", n == 89);
        check("StartListUpdates: up to the favourites max byte-identical", memcmp(out, verified_start, 83) == 0);
        check("StartListUpdates: favourites max 25 (verified asks 0)", memcmp(out + 83, "\x00\x06\x00\x08\x00\x19", 6) == 0);
    }
    {
        static struct tel_lists l;
        char text[8192];
        size_t n;
        memset(&l, 0, sizeof l);
        check("avail frame: recents count 3", tel_apply_list_update(&l, cap_avail + 6, sizeof cap_avail - 1 - 6) == 0
              && l.recents.n == 3);
        check("entries frame parses", tel_apply_list_update(&l, cap_entries + 6, sizeof cap_entries - 1 - 6) == 0);
        check("entry 0: number, name, dialed, ts, occurrences",
              strcmp(l.recents.e[0].a, "+8613800000001") == 0 && strcmp(l.recents.e[0].b, "Alice Test") == 0
              && l.recents.e[0].type == 2 && l.recents.e[0].ts == 0x69EC6754ULL && l.recents.e[0].occ == 2);
        check("entry 2: index 2, occurrences 7", l.recents.e[2].index == 2 && l.recents.e[2].occ == 7
              && l.recents.e[2].ts == 0x69457538ULL);
        check("fav frame: favourites 0, recents kept", tel_apply_list_update(&l, cap_fav + 6, sizeof cap_fav - 1 - 6) == 0
              && l.favorites.n == 0 && l.recents.n == 3);
        check("truncated frame rejected, lists unchanged", tel_apply_list_update(&l, cap_entries + 6, 40) == -1 && l.recents.n == 3);
        n = tel_lists_text(&l, text, sizeof text);
        if (getenv("TEL_TEST_DUMP")) fputs(text, stdout);
        check("bus text: object and counts", n > 0 && strncmp(text, "@telephony\nr.n:n:3\n", strlen("@telephony\nr.n:n:3\n")) == 0 && strstr(text, "\nf.n:n:0\n") != NULL);
        check("bus text: entry lines as the jar reads them", strstr(text, "\nr.0.a:s:+8613800000001\nr.0.b:s:Alice Test\n") != NULL
              && strstr(text, "\nr.0.type:n:2\nr.0.ts:n:1777100628\nr.0.occ:n:2\n") != NULL);
        check("bus text: too small refused", tel_lists_text(&l, text, 30) == 0);
        {
            /* an entry with an index past the list is dropped */
            static const unsigned char far[] = {0x00, 0x0a, 0x00, 0x01, 0x00, 0x06, 0x00, 0x00, 0x00, 0x63};
            check("index past the list dropped", tel_apply_list_update(&l, far, sizeof far) == 0 && l.recents.n == 3);
        }
    }

    printf(failures ? "%d failed\n" : "all passed\n", failures);
    return failures != 0;
}
