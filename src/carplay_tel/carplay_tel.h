// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 top11r
#ifndef CARPLAY_TEL_H
#define CARPLAY_TEL_H

#include <stddef.h>
#include <stdint.h>

/* ipod-drvr-iap2.so (K3346, sha256 0ac13f43...9d51): iap2_ctrl_msg_table is
 * 256 groups (message id >> 8) of {name, slots, count}; a slot is
 * {name, flags (1 sent by the accessory, 2 received), params}. */
struct tel_slot {
    const char *name;
    uint32_t flags;
    const void *params;
};
struct tel_group {
    const char *name;
    struct tel_slot *slots;
    int count;
};

#define TEL_GROUP 0x41
#define TEL_STOCK_SLOTS 90
#define TEL_NEW_SLOTS 0x72 /* up to ListUpdate 0x4171 */
#define TEL_MSG_START_CALL_STATE 0x4154 /* stock's subscription: the link is up */
#define TEL_MSG_TEL_UPDATE 0x4158       /* TelephonyUpdate: arrives once stock subscribed */
#define TEL_MSG_DIAL 0x415A             /* InitiateCall */
#define TEL_MSG_ACCEPT 0x415B
#define TEL_MSG_END 0x415C
#define TEL_MSG_HOLD 0x415F
#define TEL_MSG_MUTE 0x4160
#define TEL_MSG_START_LISTS 0x4170 /* StartListUpdates */
#define TEL_MSG_LIST_UPDATE 0x4171 /* ListUpdate */
#define TEL_SLOT_SEND 1u
#define TEL_SLOT_RECV 2u

/* 0 patched, 1 already patched, -1 not the stock group (nothing changed). */
int tel_patch_table(struct tel_group *table);
/* "cmd::dial\nnum::+31...\nts::123\n" -> cmd, num ("" if none), ts; 0 if cmd
 * and ts were found. */
int tel_parse(const char *text, char *cmd, size_t cmd_len, char *num, size_t num_len, long long *ts);
/* accept / end -> 0x415B / 0x415C (Action 0); hold / resume -> 0x415F (1 / 0);
 * mute / unmute -> 0x4160 (1 / 0). 0 if known, -1 otherwise (dial: tel_build_dial). */
int tel_command(const char *cmd, int *msgid, unsigned char *value);

/* InitiateCall 0x415A payload: Type 0 (destination), DestinationID, Service 1
 * (phone). Length, or 0 if the number is empty or does not fit. */
size_t tel_build_dial(const char *number, unsigned char *out, size_t max);
/* StartListUpdates 0x4170 payload: every recents property (combined) and every
 * favourites property, each list at most max entries (iOS caps CarPlay at 12). */
size_t tel_build_start_lists(unsigned char *out, size_t max, unsigned list_max);

#define TEL_LIST_MAX 20
#define TEL_TEXT_MAX 64
struct tel_entry {
    unsigned index;
    char a[TEL_TEXT_MAX]; /* RemoteID: the number */
    char b[TEL_TEXT_MAX]; /* DisplayName */
    unsigned type;        /* recents: 1 received, 2 dialed, 3 missed */
    unsigned long long ts;
    unsigned occ;
};
struct tel_list {
    unsigned n;
    struct tel_entry e[TEL_LIST_MAX];
};
struct tel_lists {
    struct tel_list recents, favorites;
};
/* Applies one ListUpdate payload to the running lists (a count resizes a list,
 * entries replace by index). 0 if the payload parsed, -1 otherwise (unchanged).
 * Not reentrant (static scratch): call under one lock. */
int tel_apply_list_update(struct tel_lists *lists, const unsigned char *payload, size_t len);
/* The bus text chefranov's jar reads (EVT_TEL_LISTS): "@telephony", then
 * r.n / r.<i>.a|b|type|ts|occ and f.n / f.<i>.a|b as key:type:value lines.
 * Length, or 0 if it does not fit. */
size_t tel_lists_text(const struct tel_lists *lists, char *out, size_t max);

#endif
