// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 top11r
/* On the unit or the emulator: the PPS command path of libcarplay_tel with a
 * fake librgd_hook. "pps_test read" runs the library's reader and prints what
 * it would send; "pps_test write <cmd> [age ms] [number]" writes a command the
 * way the HMI jar's FileOutputStream does (open with O_TRUNC, one write); a
 * number adds num:: as the jar's dial does ("write dial 0 +31612345678").
 *   arm-unknown-nto-qnx6.5.0eabi-gcc -Wl,-E -I.. pps_test.c -o pps_test */
#include "../carplay_tel.c"

int harman_link_ready(void)
{
    return 1;
}

int harman_send_message(int msgid, const void *payload, size_t len)
{
    const unsigned char *b = payload;
    size_t i;
    printf("SEND 0x%04X len %u:", msgid, (unsigned)len);
    for (i = 0; i < len; i++)
        printf(" %02x", b[i]);
    printf("\n");
    fflush(stdout);
    return 0;
}

int main(int argc, char **argv)
{
    if (argc >= 2 && strcmp(argv[1], "read") == 0) {
        read_commands();
        return 0;
    }
    if (argc >= 3 && strcmp(argv[1], "write") == 0) {
        char buf[128];
        long long age = argc >= 4 ? atoll(argv[3]) : 0;
        int fd = open(PPS_OBJ, O_WRONLY | O_CREAT | O_TRUNC, 0666);
        int n = snprintf(buf, sizeof buf, "cmd::%s\n%s%s%sts::%lld\n", argv[2], argc >= 5 ? "num::" : "",
                         argc >= 5 ? argv[4] : "", argc >= 5 ? "\n" : "", now_ms() - age);
        if (fd < 0 || n <= 0 || (size_t)n >= sizeof buf || write(fd, buf, (size_t)n) != n) {
            perror("write");
            return 1;
        }
        close(fd);
        return 0;
    }
    fprintf(stderr, "usage: pps_test read | write <cmd> [age ms] [number]\n");
    return 2;
}
