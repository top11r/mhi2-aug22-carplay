// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 top11r
package com.mhi2.settings.mount;

/* Growing the generated state machine arrays by one element (Java 1.4: no Arrays.copyOf). */
public final class SmmArrays {
    private SmmArrays() {
    }

    public static int[] append(int[] array, int value) {
        int length = array == null ? 0 : array.length;
        int[] grown = new int[length + 1];
        if (length > 0) {
            System.arraycopy(array, 0, grown, 0, length);
        }
        grown[length] = value;
        return grown;
    }

    public static int[][] append(int[][] array, int[] value) {
        int length = array == null ? 0 : array.length;
        int[][] grown = new int[length + 1][];
        if (length > 0) {
            System.arraycopy(array, 0, grown, 0, length);
        }
        grown[length] = value;
        return grown;
    }

    public static int indexOf(int[] array, int value) {
        for (int i = 0; i < array.length; i++) {
            if (array[i] == value) {
                return i;
            }
        }
        throw new IllegalStateException("no " + value);
    }
}
