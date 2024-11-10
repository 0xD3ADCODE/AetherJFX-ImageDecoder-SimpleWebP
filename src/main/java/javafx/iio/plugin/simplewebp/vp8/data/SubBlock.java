package javafx.iio.plugin.simplewebp.vp8.data;

class SubBlock {
    int[][] dest;
    int[][] diff;
    int mode;
    private final SubBlock top;
    private final SubBlock left;
    private boolean hasNoZeroToken;
    final MacroBlock macroBlock;
    private final Layer layer;
    private int[][] predict;
    private int[] tokens;

    static int layerToType(final Layer layer, final Boolean hasY2) {
        switch (layer) {
            case Y2: {
                return 1;
            }
            case Y1: {
                return hasY2 ? 0 : 3;
            }
            case U:
            case V: {
                return 2;
            }
            default: {
                return -1;
            }
        }
    }

    SubBlock(final MacroBlock macroBlock, final SubBlock top, final SubBlock left, final Layer layer) {
        this.macroBlock = macroBlock;
        this.layer = layer;
        this.top = top;
        this.left = left;
        this.mode = 0;
        this.tokens = new int[16];
    }

    private static int getExtraDCT(final BitDecoder bc2, final int[] p) {
        int v = 0;
        int offset = 0;
        do {
            v += v + bc2.getProbBit(p[offset]);
            ++offset;
        } while (p[offset] > 0);
        return v;
    }

    void decode(final BitDecoder bc2, final int[][][][] coef_probs, final int ilc, final int type, final boolean hasY2) {
        int startAt = 0;
        if (hasY2) {
            startAt = 1;
        }
        int lc = ilc;
        int c = 0;
        int v = 1;
        boolean skip = false;
        while (v != 11 && c + startAt < 16) {
            if (!skip) {
                v = bc2.getTree(LookUp.EOB_COEF_TREE, coef_probs[type][LookUp.CO_BANDS[c + startAt]][lc]);
            } else {
                v = bc2.skipTree(coef_probs[type][LookUp.CO_BANDS[c + startAt]][lc]);
            }
            final int dv = decodeToken(bc2, v);
            lc = 0;
            skip = false;
            if (dv == 1 || dv == -1) {
                lc = 1;
            } else if (dv > 1 || dv < -1) {
                lc = 2;
            } else {
                skip = true;
            }
            if (v != 11) {
                this.tokens[LookUp.ZIGZAGS[c + startAt]] = dv;
            }
            ++c;
        }
        this.hasNoZeroToken = false;
        for (int x = 0; x < 16; ++x) {
            if (this.tokens[x] != 0) {
                this.hasNoZeroToken = true;
                break;
            }
        }
    }

    private static int decodeToken(final BitDecoder bc2, final int v) {
        int r = v;
        switch (v) {
            case 5: {
                r = 5 + getExtraDCT(bc2, LookUp.PC1);
                break;
            }
            case 6: {
                r = 7 + getExtraDCT(bc2, LookUp.PC2);
                break;
            }
            case 7: {
                r = 11 + getExtraDCT(bc2, LookUp.PC3);
                break;
            }
            case 8: {
                r = 19 + getExtraDCT(bc2, LookUp.PC4);
                break;
            }
            case 9: {
                r = 35 + getExtraDCT(bc2, LookUp.PC5);
                break;
            }
            case 10: {
                r = 67 + getExtraDCT(bc2, LookUp.PC6);
                break;
            }
        }
        if (v != 0 && v != 11 && bc2.getBit() > 0) {
            r = -r;
        }
        return r;
    }

    void dequantSubBlock(final Frame frame, final Integer Dc) {
        final int[] adjustedValues = new int[16];
        for (int i = 0; i < 16; ++i) {
            int dq;
            if (this.layer == Layer.U || this.layer == Layer.V) {
                dq = frame.getSegmentQuants().segQuants[this.macroBlock.key].uvac;
                if (i == 0) {
                    dq = frame.getSegmentQuants().segQuants[this.macroBlock.key].uvdc;
                }
            } else {
                dq = frame.getSegmentQuants().segQuants[this.macroBlock.key].y1ac;
                if (i == 0) {
                    dq = frame.getSegmentQuants().segQuants[this.macroBlock.key].y1dc;
                }
            }
            adjustedValues[i] = this.tokens[i] * dq;
        }
        if (Dc != null) {
            adjustedValues[0] = Dc;
        }
        this.diff = Transform.cosine(adjustedValues, this.macroBlock.cache16);
    }

    SubBlock getAbove() {
        return this.top;
    }

    int[][] getDest() {
        if (this.dest != null) {
            return this.dest;
        }
        return new int[4][4];
    }

    int[][] getDiff() {
        return this.diff;
    }

    SubBlock getLeft() {
        return this.left;
    }

    int[][] getMacroBlockPredict(final int intra_mode) {
        if (this.dest != null) {
            return this.dest;
        }
        int rv = 127;
        if (intra_mode == 2) {
            rv = 129;
        }
        final int[][] r = new int[4][4];
        for (int j = 0; j < 4; ++j) {
            for (int i = 0; i < 4; ++i) {
                r[i][j] = rv;
            }
        }
        return r;
    }

    Layer getLayer() {
        return this.layer;
    }

    int[][] getPredict(final int bMode, final boolean left) {
        if (this.dest != null) {
            return this.dest;
        }
        if (this.predict != null) {
            return this.predict;
        }
        int rv = 127;
        if ((bMode == 1 || bMode == 0 || bMode == 2 || bMode == 3 || bMode == 6 || bMode == 5 || bMode == 8) && left) {
            rv = 129;
        }
        final int[][] r = new int[4][4];
        for (int j = 0; j < 4; ++j) {
            for (int i = 0; i < 4; ++i) {
                r[i][j] = rv;
            }
        }
        return r;
    }

    int[] getTokens() {
        return this.tokens;
    }

    boolean hasNoZeroToken() {
        return this.hasNoZeroToken;
    }

    private boolean isDest() {
        return this.dest != null;
    }

    void predict(final Frame frame) {
        final SubBlock aboveSb = frame.getTopSubBlock(this, this.layer);
        final SubBlock leftSb = frame.getLeftSubBlock(this, this.layer);
        final int[] top = new int[4];
        final int[] left = new int[4];
        top[0] = aboveSb.getPredict(this.mode, false)[0][3];
        top[1] = aboveSb.getPredict(this.mode, false)[1][3];
        top[2] = aboveSb.getPredict(this.mode, false)[2][3];
        top[3] = aboveSb.getPredict(this.mode, false)[3][3];
        left[0] = leftSb.getPredict(this.mode, true)[3][0];
        left[1] = leftSb.getPredict(this.mode, true)[3][1];
        left[2] = leftSb.getPredict(this.mode, true)[3][2];
        left[3] = leftSb.getPredict(this.mode, true)[3][3];
        final SubBlock AL = frame.getLeftSubBlock(aboveSb, this.layer);
        int al;
        if (!leftSb.isDest() && !aboveSb.isDest()) {
            al = AL.getPredict(this.mode, false)[3][3];
        } else if (!aboveSb.isDest()) {
            al = AL.getPredict(this.mode, false)[3][3];
        } else {
            al = AL.getPredict(this.mode, true)[3][3];
        }
        final SubBlock AR = frame.getTopRightSubBlock(this, this.layer);
        final int[] ar = {AR.getPredict(this.mode, false)[0][3], AR.getPredict(this.mode, false)[1][3], AR.getPredict(this.mode, false)[2][3], AR.getPredict(this.mode, false)[3][3]};
        switch (this.mode) {
            case 0: {
                this.setB_DC_PRED(top, left);
                break;
            }
            case 1: {
                this.setB_TM_PRED(top, al, left);
                break;
            }
            case 2: {
                this.setB_VE_PRED(al, top, ar);
                break;
            }
            case 3: {
                this.setB_HE_PRED(al, left);
                break;
            }
            case 4: {
                this.setB_LD_PRED(top, ar);
                break;
            }
            case 5: {
                this.setB_RD_PRED(top, left, al);
                break;
            }
            case 6: {
                this.setB_VR_PRED(top, left, al);
                break;
            }
            case 7: {
                this.setB_VL_PRED(top, ar);
                break;
            }
            case 8: {
                this.setB_HD_PRED(top, left, al);
                break;
            }
            case 9: {
                this.setB_HU_PRED(left);
                break;
            }
        }
    }

    private void setB_HE_PRED(final int al, final int[] left1) {
        final int[][] p = new int[4][4];
        final int[] lp = {al + 2 * left1[0] + left1[1] + 2 >> 2, left1[0] + 2 * left1[1] + left1[2] + 2 >> 2, left1[1] + 2 * left1[2] + left1[3] + 2 >> 2, left1[2] + 2 * left1[3] + left1[3] + 2 >> 2};
        for (int r = 0; r < 4; ++r) {
            for (int c = 0; c < 4; ++c) {
                p[c][r] = lp[r];
            }
        }
        this.predict = p;
    }

    private void setB_VE_PRED(final int al, final int[] top1, final int[] ar) {
        final int[][] p = new int[4][4];
        final int[] ap = {al + 2 * top1[0] + top1[1] + 2 >> 2, top1[0] + 2 * top1[1] + top1[2] + 2 >> 2, top1[1] + 2 * top1[2] + top1[3] + 2 >> 2, top1[2] + 2 * top1[3] + ar[0] + 2 >> 2};
        for (int r = 0; r < 4; ++r) {
            for (int c = 0; c < 4; ++c) {
                p[c][r] = ap[c];
            }
        }
        this.predict = p;
    }

    private void setB_TM_PRED(final int[] top1, final int al, final int[] left1) {
        final int[][] p = new int[4][4];
        for (int r = 0; r < 4; ++r) {
            for (int c = 0; c < 4; ++c) {
                int pred = top1[c] - al + left1[r];
                if (pred < 0) {
                    pred = 0;
                }
                if (pred > 255) {
                    pred = 255;
                }
                p[c][r] = pred;
            }
        }
        this.predict = p;
    }

    private void setB_DC_PRED(final int[] top1, final int[] left1) {
        final int[][] p = new int[4][4];
        int expected_dc = 0;
        for (int i = 0; i < 4; ++i) {
            expected_dc += top1[i];
            expected_dc += left1[i];
        }
        expected_dc = expected_dc + 4 >> 3;
        for (int y = 0; y < 4; ++y) {
            for (int x = 0; x < 4; ++x) {
                p[x][y] = expected_dc;
            }
        }
        this.predict = p;
    }

    private void setB_LD_PRED(final int[] top, final int[] ar) {
        final int[][] p = new int[4][4];
        p[0][0] = top[0] + top[1] * 2 + top[2] + 2 >> 2;
        p[1][0] = (p[0][1] = top[1] + top[2] * 2 + top[3] + 2 >> 2);
        final int[] array = p[2];
        final int n = 0;
        final int[] array2 = p[1];
        final int n2 = 1;
        final int[] array3 = p[0];
        final int n3 = 2;
        final int n4 = top[2] + top[3] * 2 + ar[0] + 2 >> 2;
        array3[n3] = n4;
        array[n] = (array2[n2] = n4);
        final int[] array4 = p[3];
        final int n5 = 0;
        final int[] array5 = p[2];
        final int n6 = 1;
        final int[] array6 = p[1];
        final int n7 = 2;
        final int[] array7 = p[0];
        final int n8 = 3;
        final int n9 = top[3] + ar[0] * 2 + ar[1] + 2 >> 2;
        array6[n7] = (array7[n8] = n9);
        array4[n5] = (array5[n6] = n9);
        final int[] array8 = p[3];
        final int n10 = 1;
        final int[] array9 = p[2];
        final int n11 = 2;
        final int[] array10 = p[1];
        final int n12 = 3;
        final int n13 = ar[0] + ar[1] * 2 + ar[2] + 2 >> 2;
        array10[n12] = n13;
        array8[n10] = (array9[n11] = n13);
        p[3][2] = (p[2][3] = ar[1] + ar[2] * 2 + ar[3] + 2 >> 2);
        p[3][3] = ar[2] + ar[3] * 2 + ar[3] + 2 >> 2;
        this.predict = p;
    }

    private void setB_RD_PRED(final int[] top, final int[] left, final int al) {
        final int[] pp = new int[9];
        final int[][] p = new int[4][4];
        pp[0] = left[3];
        pp[1] = left[2];
        pp[2] = left[1];
        pp[3] = left[0];
        pp[4] = al;
        pp[5] = top[0];
        pp[6] = top[1];
        pp[7] = top[2];
        pp[8] = top[3];
        p[0][3] = pp[0] + pp[1] * 2 + pp[2] + 2 >> 2;
        p[1][3] = (p[0][2] = pp[1] + pp[2] * 2 + pp[3] + 2 >> 2);
        final int[] array = p[2];
        final int n = 3;
        final int[] array2 = p[1];
        final int n2 = 2;
        final int[] array3 = p[0];
        final int n3 = 1;
        final int n4 = pp[2] + pp[3] * 2 + pp[4] + 2 >> 2;
        array3[n3] = n4;
        array[n] = (array2[n2] = n4);
        final int[] array4 = p[3];
        final int n5 = 3;
        final int[] array5 = p[2];
        final int n6 = 2;
        final int[] array6 = p[1];
        final int n7 = 1;
        final int[] array7 = p[0];
        final int n8 = 0;
        final int n9 = pp[3] + pp[4] * 2 + pp[5] + 2 >> 2;
        array6[n7] = (array7[n8] = n9);
        array4[n5] = (array5[n6] = n9);
        final int[] array8 = p[3];
        final int n10 = 2;
        final int[] array9 = p[2];
        final int n11 = 1;
        final int[] array10 = p[1];
        final int n12 = 0;
        final int n13 = pp[4] + pp[5] * 2 + pp[6] + 2 >> 2;
        array10[n12] = n13;
        array8[n10] = (array9[n11] = n13);
        p[3][1] = (p[2][0] = pp[5] + pp[6] * 2 + pp[7] + 2 >> 2);
        p[3][0] = pp[6] + pp[7] * 2 + pp[8] + 2 >> 2;
        this.predict = p;
    }

    private void setB_VR_PRED(final int[] top, final int[] left, final int al) {
        final int[][] p = new int[4][4];
        final int pp1 = left[2];
        final int pp2 = left[1];
        final int pp3 = left[0];
        final int pp4 = al;
        final int pp5 = top[0];
        final int pp6 = top[1];
        final int pp7 = top[2];
        final int pp8 = top[3];
        p[0][3] = pp1 + pp2 * 2 + pp3 + 2 >> 2;
        p[0][2] = pp2 + pp3 * 2 + pp4 + 2 >> 2;
        p[1][3] = (p[0][1] = pp3 + pp4 * 2 + pp5 + 2 >> 2);
        p[1][2] = (p[0][0] = pp4 + pp5 + 1 >> 1);
        p[2][3] = (p[1][1] = pp4 + pp5 * 2 + pp6 + 2 >> 2);
        p[2][2] = (p[1][0] = pp5 + pp6 + 1 >> 1);
        p[3][3] = (p[2][1] = pp5 + pp6 * 2 + pp7 + 2 >> 2);
        p[3][2] = (p[2][0] = pp6 + pp7 + 1 >> 1);
        p[3][1] = pp6 + pp7 * 2 + pp8 + 2 >> 2;
        p[3][0] = pp7 + pp8 + 1 >> 1;
        this.predict = p;
    }

    private void setB_VL_PRED(final int[] top, final int[] ar) {
        final int[][] p = new int[4][4];
        p[0][0] = top[0] + top[1] + 1 >> 1;
        p[0][1] = top[0] + top[1] * 2 + top[2] + 2 >> 2;
        p[0][2] = (p[1][0] = top[1] + top[2] + 1 >> 1);
        p[1][1] = (p[0][3] = top[1] + top[2] * 2 + top[3] + 2 >> 2);
        p[1][2] = (p[2][0] = top[2] + top[3] + 1 >> 1);
        p[1][3] = (p[2][1] = top[2] + top[3] * 2 + ar[0] + 2 >> 2);
        p[3][0] = (p[2][2] = top[3] + ar[0] + 1 >> 1);
        p[3][1] = (p[2][3] = top[3] + ar[0] * 2 + ar[1] + 2 >> 2);
        p[3][2] = ar[0] + ar[1] * 2 + ar[2] + 2 >> 2;
        p[3][3] = ar[1] + ar[2] * 2 + ar[3] + 2 >> 2;
        this.predict = p;
    }

    private void setB_HD_PRED(final int[] top, final int[] left, final int al) {
        final int[][] p = new int[4][4];
        final int[] pp = {left[3], left[2], left[1], left[0], al, top[0], top[1], top[2], top[3]};
        p[0][3] = pp[0] + pp[1] + 1 >> 1;
        p[1][3] = pp[0] + pp[1] * 2 + pp[2] + 2 >> 2;
        p[0][2] = (p[2][3] = pp[1] + pp[2] + 1 >> 1);
        p[1][2] = (p[3][3] = pp[1] + pp[2] * 2 + pp[3] + 2 >> 2);
        p[2][2] = (p[0][1] = pp[2] + pp[3] + 1 >> 1);
        p[3][2] = (p[1][1] = pp[2] + pp[3] * 2 + pp[4] + 2 >> 2);
        p[2][1] = (p[0][0] = pp[3] + pp[4] + 1 >> 1);
        p[3][1] = (p[1][0] = pp[3] + pp[4] * 2 + pp[5] + 2 >> 2);
        p[2][0] = pp[4] + pp[5] * 2 + pp[6] + 2 >> 2;
        p[3][0] = pp[5] + pp[6] * 2 + pp[7] + 2 >> 2;
        this.predict = p;
    }

    private void setB_HU_PRED(final int[] left) {
        final int[][] p = new int[4][4];
        p[0][0] = left[0] + left[1] + 1 >> 1;
        p[1][0] = left[0] + left[1] * 2 + left[2] + 2 >> 2;
        p[2][0] = (p[0][1] = left[1] + left[2] + 1 >> 1);
        p[3][0] = (p[1][1] = left[1] + left[2] * 2 + left[3] + 2 >> 2);
        p[2][1] = (p[0][2] = left[2] + left[3] + 1 >> 1);
        p[3][1] = (p[1][2] = left[2] + left[3] * 2 + left[3] + 2 >> 2);
        final int[] array = p[2];
        final int n = 2;
        final int[] array2 = p[3];
        final int n2 = 2;
        final int[] array3 = p[0];
        final int n3 = 3;
        final int[] array4 = p[1];
        final int n4 = 3;
        final int[] array5 = p[2];
        final int n5 = 3;
        final int[] array6 = p[3];
        final int n6 = 3;
        final int n7 = left[3];
        array5[n5] = (array6[n6] = n7);
        array3[n3] = (array4[n4] = n7);
        array[n] = (array2[n2] = n7);
        this.predict = p;
    }

    void reconstruct() {
        final int[][] p = this.getPredict(1, false);
        final int[][] dd = new int[4][4];
        for (int r = 0; r < 4; ++r) {
            for (int c = 0; c < 4; ++c) {
                final int a = this.diff[r][c] + p[r][c];
                dd[r][c] = ((a < 0) ? 0 : ((a > 255) ? 255 : a));
            }
        }
        this.dest = dd;
        this.diff = null;
        this.predict = null;
        this.tokens = null;
    }

    void setMode(final int mode) {
        this.mode = mode;
    }

    void setPixel(final int x, final int y, final int p) {
        if (this.dest == null) {
            this.dest = new int[4][4];
        }
        this.dest[x][y] = p;
    }

    void setPredict(final int[][] predict) {
        this.predict = predict;
    }

    enum Layer {
        U,
        V,
        Y1,
        Y2
    }
}
