package javafx.iio.plugin.simplewebp.vp8.data;

final class Transform {
    private Transform() {
    }

    static int[][] cosine(final int[] input, final int[] output) {
        int offset = 0;
        for (int i = 0; i < 4; ++i) {
            final int i2 = input[offset];
            final int i3 = input[offset + 4];
            final int i4 = input[offset + 8];
            final int i5 = input[offset + 12];
            final int a1 = i2 + i4;
            final int b1 = i2 - i4;
            int t1 = i3 * 35468 >> 16;
            int t2 = i5 + (i5 * 20091 >> 16);
            final int c1 = t1 - t2;
            t1 = i3 + (i3 * 20091 >> 16);
            t2 = i5 * 35468 >> 16;
            final int d1 = t1 + t2;
            output[offset] = a1 + d1;
            output[offset + 12] = a1 - d1;
            output[offset + 4] = b1 + c1;
            output[offset + 8] = b1 - c1;
            ++offset;
        }
        int diffo = 0;
        final int[][] diff = new int[4][4];
        offset = 0;
        for (int i = 0; i < 4; ++i) {
            final int o4 = offset * 4;
            final int a1 = output[o4] + output[o4 + 2];
            final int b1 = output[o4] - output[o4 + 2];
            int t1 = output[o4 + 1] * 35468 >> 16;
            int t2 = output[o4 + 3] + (output[o4 + 3] * 20091 >> 16);
            final int c1 = t1 - t2;
            t1 = output[o4 + 1] + (output[o4 + 1] * 20091 >> 16);
            t2 = output[o4 + 3] * 35468 >> 16;
            final int d1 = t1 + t2;
            output[o4] = a1 + d1 + 4 >> 3;
            output[o4 + 3] = a1 - d1 + 4 >> 3;
            output[o4 + 1] = b1 + c1 + 4 >> 3;
            output[o4 + 2] = b1 - c1 + 4 >> 3;
            diff[0][diffo] = a1 + d1 + 4 >> 3;
            diff[3][diffo] = a1 - d1 + 4 >> 3;
            diff[1][diffo] = b1 + c1 + 4 >> 3;
            diff[2][diffo] = b1 - c1 + 4 >> 3;
            ++offset;
            ++diffo;
        }
        return diff;
    }

    static int[][] walsh(final int[] input, final int[] output) {
        final int[][] diff = new int[4][4];
        int offset = 0;
        for (int i = 0; i < 4; ++i) {
            final int a1 = input[offset] + input[offset + 12];
            final int b1 = input[offset + 4] + input[offset + 8];
            final int c1 = input[offset + 4] - input[offset + 8];
            final int d1 = input[offset] - input[offset + 12];
            output[offset] = a1 + b1;
            output[offset + 4] = c1 + d1;
            output[offset + 8] = a1 - b1;
            output[offset + 12] = d1 - c1;
            ++offset;
        }
        offset = 0;
        for (int i = 0; i < 4; ++i) {
            final int a1 = output[offset] + output[offset + 3];
            final int b1 = output[offset + 1] + output[offset + 2];
            final int c1 = output[offset + 1] - output[offset + 2];
            final int d1 = output[offset] - output[offset + 3];
            final int a2 = a1 + b1;
            final int b2 = c1 + d1;
            final int c2 = a1 - b1;
            final int d2 = d1 - c1;
            output[offset] = a2 + 3 >> 3;
            output[offset + 1] = b2 + 3 >> 3;
            output[offset + 2] = c2 + 3 >> 3;
            output[offset + 3] = d2 + 3 >> 3;
            diff[0][i] = a2 + 3 >> 3;
            diff[1][i] = b2 + 3 >> 3;
            diff[2][i] = c2 + 3 >> 3;
            diff[3][i] = d2 + 3 >> 3;
            offset += 4;
        }
        return diff;
    }
}
