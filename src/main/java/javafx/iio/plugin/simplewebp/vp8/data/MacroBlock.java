package javafx.iio.plugin.simplewebp.vp8.data;

class MacroBlock {
    private int filterLevel;
    int key;
    private int skipCoeff;
    private boolean innerLoopSkip;
    private final SubBlock[][] uSubBlocks;
    private int uvMode;
    private final SubBlock[][] vSubBlocks;
    private final int x;
    private final int y;
    private final SubBlock y2SubBlock;
    private int yMode;
    private final SubBlock[][] ySubBlocks;
    final int[] cache16;

    MacroBlock(final int x, final int y, final int[] cache16) {
        this.x = x - 1;
        this.y = y - 1;
        this.cache16 = cache16;
        this.ySubBlocks = new SubBlock[4][4];
        this.uSubBlocks = new SubBlock[2][2];
        this.vSubBlocks = new SubBlock[2][2];
        for (int i = 0; i < 4; ++i) {
            for (int j = 0; j < 4; ++j) {
                SubBlock left = null;
                SubBlock above = null;
                if (j > 0) {
                    left = this.ySubBlocks[j - 1][i];
                }
                if (i > 0) {
                    above = this.ySubBlocks[j][i - 1];
                }
                this.ySubBlocks[j][i] = new SubBlock(this, above, left, SubBlock.Layer.Y1);
            }
        }
        for (int i = 0; i < 2; ++i) {
            for (int j = 0; j < 2; ++j) {
                SubBlock left = null;
                SubBlock above = null;
                if (j > 0) {
                    left = this.uSubBlocks[j - 1][i];
                }
                if (i > 0) {
                    above = this.uSubBlocks[j][i - 1];
                }
                this.uSubBlocks[j][i] = new SubBlock(this, above, left, SubBlock.Layer.U);
            }
        }
        for (int i = 0; i < 2; ++i) {
            for (int j = 0; j < 2; ++j) {
                SubBlock left = null;
                SubBlock above = null;
                if (j > 0) {
                    left = this.vSubBlocks[j - 1][i];
                }
                if (i > 0) {
                    above = this.vSubBlocks[j][i - 1];
                }
                this.vSubBlocks[j][i] = new SubBlock(this, above, left, SubBlock.Layer.V);
            }
        }
        this.y2SubBlock = new SubBlock(this, null, null, SubBlock.Layer.Y2);
    }

    void decodeMacroBlock(final Frame frame) {
        if (this.skipCoeff > 0) {
            if (this.yMode != 4) {
                this.innerLoopSkip = true;
            }
        } else {
            this.decodeMacroBlockTokens(frame, this.yMode != 4);
        }
    }

    private void decodeMacroBlockTokens(final Frame frame, final boolean withY2) {
        this.innerLoopSkip = false;
        if (withY2) {
            this.decodePlaneTokens(frame, 1, SubBlock.Layer.Y2, false);
        }
        this.decodePlaneTokens(frame, 4, SubBlock.Layer.Y1, withY2);
        this.decodePlaneTokens(frame, 2, SubBlock.Layer.U, false);
        this.decodePlaneTokens(frame, 2, SubBlock.Layer.V, false);
    }

    private void decodePlaneTokens(final Frame frame, final int dimentions, final SubBlock.Layer plane, final boolean withY2) {
        final MacroBlock mb = this;
        for (int i = 0; i < dimentions; ++i) {
            for (int j = 0; j < dimentions; ++j) {
                int L = 0;
                int A = 0;
                int lc = 0;
                final SubBlock sb = mb.getSubBlock(plane, j, i);
                final SubBlock left = frame.getLeftSubBlock(sb, plane);
                final SubBlock above = frame.getTopSubBlock(sb, plane);
                if (left.hasNoZeroToken()) {
                    L = 1;
                }
                lc += L;
                if (above.hasNoZeroToken()) {
                    A = 1;
                }
                lc += A;
                sb.decode(frame.getTokenBoolDecoder(), frame.getCoefProbs(), lc, SubBlock.layerToType(plane, withY2), withY2);
                sb.hasNoZeroToken();
            }
        }
    }

    void dequantMacroBlock(final Frame frame) {
        final MacroBlock mb = this;
        if (mb.yMode != 4) {
            final SubBlock sb = mb.y2SubBlock;
            final int acQValue = frame.getSegmentQuants().segQuants[this.key].y2ac;
            final int dcQValue = frame.getSegmentQuants().segQuants[this.key].y2dc;
            final int[] input = getInput(sb, acQValue, dcQValue);
            sb.diff = Transform.walsh(input, this.cache16);
            for (int j = 0; j < 4; ++j) {
                for (int i = 0; i < 4; ++i) {
                    final SubBlock ysb = mb.getYSubBlock(i, j);
                    ysb.dequantSubBlock(frame, sb.getDiff()[i][j]);
                }
            }
            mb.predictY(frame);
            mb.predictUV(frame);
            for (int i = 0; i < 2; ++i) {
                for (int j = 0; j < 2; ++j) {
                    SubBlock uvsb = mb.getUSubBlock(j, i);
                    uvsb.dequantSubBlock(frame, null);
                    uvsb = mb.getVSubBlock(i, j);
                    uvsb.dequantSubBlock(frame, null);
                }
            }
            mb.recon_mb();
        } else {
            for (int j = 0; j < 4; ++j) {
                for (int i = 0; i < 4; ++i) {
                    final SubBlock sb = mb.getYSubBlock(i, j);
                    sb.dequantSubBlock(frame, null);
                    sb.predict(frame);
                    sb.reconstruct();
                }
            }
            mb.predictUV(frame);
            for (int i = 0; i < 2; ++i) {
                for (int j = 0; j < 2; ++j) {
                    final SubBlock sb = mb.getUSubBlock(j, i);
                    sb.dequantSubBlock(frame, null);
                    sb.reconstruct();
                }
            }
            for (int i = 0; i < 2; ++i) {
                for (int j = 0; j < 2; ++j) {
                    final SubBlock sb = mb.getVSubBlock(j, i);
                    sb.dequantSubBlock(frame, null);
                    sb.reconstruct();
                }
            }
        }
    }

    private static int[] getInput(final SubBlock sb, final int acQValue, final int dcQValue) {
        final int[] input = new int[16];
        input[0] = sb.getTokens()[0] * dcQValue;
        for (int x = 1; x < 16; ++x) {
            input[x] = sb.getTokens()[x] * acQValue;
        }
        return input;
    }

    SubBlock getBottomSubBlock(final int x, final SubBlock.Layer plane) {
        switch (plane) {
            case Y1: {
                return this.ySubBlocks[x][3];
            }
            case U: {
                return this.uSubBlocks[x][1];
            }
            case V: {
                return this.vSubBlocks[x][1];
            }
            case Y2: {
                return this.y2SubBlock;
            }
            default: {
                return null;
            }
        }
    }

    int getFilterLevel() {
        return this.filterLevel;
    }

    SubBlock getRightSubBlock(final int y, final SubBlock.Layer plane) {
        if (null != plane) {
            switch (plane) {
                case Y1: {
                    return this.ySubBlocks[3][y];
                }
                case U: {
                    return this.uSubBlocks[1][y];
                }
                case V: {
                    return this.vSubBlocks[1][y];
                }
                case Y2: {
                    return this.y2SubBlock;
                }
            }
        }
        return null;
    }

    SubBlock getSubBlock(final SubBlock.Layer plane, final int i, final int j) {
        switch (plane) {
            case Y1: {
                return this.getYSubBlock(i, j);
            }
            case U: {
                return this.getUSubBlock(i, j);
            }
            case V: {
                return this.getVSubBlock(i, j);
            }
            case Y2: {
                return this.y2SubBlock;
            }
            default: {
                return null;
            }
        }
    }

    int getSubblockX(final SubBlock sb) {
        if (null != sb.getLayer()) {
            switch (sb.getLayer()) {
                case Y1: {
                    for (int y = 0; y < 4; ++y) {
                        for (int x = 0; x < 4; ++x) {
                            if (this.ySubBlocks[x][y] == sb) {
                                return x;
                            }
                        }
                    }
                    break;
                }
                case U: {
                    for (int y = 0; y < 2; ++y) {
                        for (int x = 0; x < 2; ++x) {
                            if (this.uSubBlocks[x][y] == sb) {
                                return x;
                            }
                        }
                    }
                    break;
                }
                case V: {
                    for (int y = 0; y < 2; ++y) {
                        for (int x = 0; x < 2; ++x) {
                            if (this.vSubBlocks[x][y] == sb) {
                                return x;
                            }
                        }
                    }
                    break;
                }
                case Y2: {
                    return 0;
                }
            }
        }
        return -100;
    }

    int getSubblockY(final SubBlock sb) {
        if (null != sb.getLayer()) {
            switch (sb.getLayer()) {
                case Y1: {
                    for (int y = 0; y < 4; ++y) {
                        for (int x = 0; x < 4; ++x) {
                            if (this.ySubBlocks[x][y] == sb) {
                                return y;
                            }
                        }
                    }
                    break;
                }
                case U: {
                    for (int y = 0; y < 2; ++y) {
                        for (int x = 0; x < 2; ++x) {
                            if (this.uSubBlocks[x][y] == sb) {
                                return y;
                            }
                        }
                    }
                    break;
                }
                case V: {
                    for (int y = 0; y < 2; ++y) {
                        for (int x = 0; x < 2; ++x) {
                            if (this.vSubBlocks[x][y] == sb) {
                                return y;
                            }
                        }
                    }
                    break;
                }
                case Y2: {
                    return 0;
                }
            }
        }
        return -100;
    }

    private SubBlock getUSubBlock(final int i, final int j) {
        return this.uSubBlocks[i][j];
    }

    private SubBlock getVSubBlock(final int i, final int j) {
        return this.vSubBlocks[i][j];
    }

    int getX() {
        return this.x;
    }

    int getY() {
        return this.y;
    }

    int getYMode() {
        return this.yMode;
    }

    SubBlock getYSubBlock(final int i, final int j) {
        return this.ySubBlocks[i][j];
    }

    boolean isSkip_inner_lf() {
        return this.innerLoopSkip;
    }

    private void predictUV(final Frame frame) {
        final MacroBlock aboveMb = frame.getMacroBlock(this.x, this.y - 1);
        final MacroBlock leftMb = frame.getMacroBlock(this.x - 1, this.y);
        switch (this.uvMode) {
            case 0: {
                this.doDCPredict(aboveMb, leftMb);
                break;
            }
            case 1: {
                this.doVPredict(aboveMb);
                break;
            }
            case 2: {
                this.doHPredict(leftMb);
                break;
            }
            case 3: {
                this.doTMPredict(aboveMb, leftMb, frame);
                break;
            }
        }
    }

    private void doDCPredict(final MacroBlock aboveMb, final MacroBlock leftMb) {
        boolean up_available = false;
        boolean left_available = false;
        int Uaverage = 0;
        int Vaverage = 0;
        if (this.x > 0) {
            left_available = true;
        }
        if (this.y > 0) {
            up_available = true;
        }
        int expected_udc;
        int expected_vdc;
        if (up_available || left_available) {
            if (up_available) {
                for (int j = 0; j < 2; ++j) {
                    final SubBlock usb = aboveMb.getUSubBlock(j, 1);
                    final SubBlock vsb = aboveMb.getVSubBlock(j, 1);
                    for (int i = 0; i < 4; ++i) {
                        Uaverage += usb.getDest()[i][3];
                        Vaverage += vsb.getDest()[i][3];
                    }
                }
            }
            if (left_available) {
                for (int j = 0; j < 2; ++j) {
                    final SubBlock usb = leftMb.getUSubBlock(1, j);
                    final SubBlock vsb = leftMb.getVSubBlock(1, j);
                    for (int i = 0; i < 4; ++i) {
                        Uaverage += usb.getDest()[3][i];
                        Vaverage += vsb.getDest()[3][i];
                    }
                }
            }
            int shift = 2;
            if (up_available) {
                ++shift;
            }
            if (left_available) {
                ++shift;
            }
            expected_udc = Uaverage + (1 << shift - 1) >> shift;
            expected_vdc = Vaverage + (1 << shift - 1) >> shift;
        } else {
            expected_udc = 128;
            expected_vdc = 128;
        }
        final int[][] ufill = new int[4][4];
        for (int y = 0; y < 4; ++y) {
            for (int x = 0; x < 4; ++x) {
                ufill[x][y] = expected_udc;
            }
        }
        final int[][] vfill = new int[4][4];
        for (int y2 = 0; y2 < 4; ++y2) {
            for (int x2 = 0; x2 < 4; ++x2) {
                vfill[x2][y2] = expected_vdc;
            }
        }
        for (int y2 = 0; y2 < 2; ++y2) {
            for (int x2 = 0; x2 < 2; ++x2) {
                final SubBlock usb2 = this.uSubBlocks[x2][y2];
                final SubBlock vsb2 = this.vSubBlocks[x2][y2];
                usb2.setPredict(ufill);
                vsb2.setPredict(vfill);
            }
        }
    }

    private void doVPredict(final MacroBlock aboveMb) {
        final SubBlock[] aboveUSb = new SubBlock[2];
        final SubBlock[] aboveVSb = new SubBlock[2];
        for (int x = 0; x < 2; ++x) {
            aboveUSb[x] = aboveMb.getUSubBlock(x, 1);
            aboveVSb[x] = aboveMb.getVSubBlock(x, 1);
        }
        for (int y = 0; y < 2; ++y) {
            for (int x2 = 0; x2 < 2; ++x2) {
                final SubBlock usb = this.uSubBlocks[y][x2];
                final SubBlock vsb = this.vSubBlocks[y][x2];
                final int[][] ublock = new int[4][4];
                final int[][] vblock = new int[4][4];
                for (int j = 0; j < 4; ++j) {
                    for (int i = 0; i < 4; ++i) {
                        ublock[j][i] = aboveUSb[y].getMacroBlockPredict(1)[j][3];
                        vblock[j][i] = aboveVSb[y].getMacroBlockPredict(1)[j][3];
                    }
                }
                usb.setPredict(ublock);
                vsb.setPredict(vblock);
            }
        }
    }

    private void doHPredict(final MacroBlock leftMb) {
        final SubBlock[] leftUSb = new SubBlock[2];
        final SubBlock[] leftVSb = new SubBlock[2];
        for (int x = 0; x < 2; ++x) {
            leftUSb[x] = leftMb.getUSubBlock(1, x);
            leftVSb[x] = leftMb.getVSubBlock(1, x);
        }
        for (int y = 0; y < 2; ++y) {
            for (int x2 = 0; x2 < 2; ++x2) {
                final SubBlock usb = this.uSubBlocks[x2][y];
                final SubBlock vsb = this.vSubBlocks[x2][y];
                final int[][] ublock = new int[4][4];
                final int[][] vblock = new int[4][4];
                for (int j = 0; j < 4; ++j) {
                    for (int i = 0; i < 4; ++i) {
                        ublock[i][j] = leftUSb[y].getMacroBlockPredict(2)[3][j];
                        vblock[i][j] = leftVSb[y].getMacroBlockPredict(2)[3][j];
                    }
                }
                usb.setPredict(ublock);
                vsb.setPredict(vblock);
            }
        }
    }

    private void doTMPredict(final MacroBlock aboveMb, final MacroBlock leftMb, final Frame frame) {
        final MacroBlock ALMb = frame.getMacroBlock(this.x - 1, this.y - 1);
        final SubBlock ALUSb = ALMb.getUSubBlock(1, 1);
        final int alu = ALUSb.getDest()[3][3];
        final SubBlock ALVSb = ALMb.getVSubBlock(1, 1);
        final int alv = ALVSb.getDest()[3][3];
        final SubBlock[] aboveUSb = new SubBlock[2];
        final SubBlock[] leftUSb = new SubBlock[2];
        final SubBlock[] aboveVSb = new SubBlock[2];
        final SubBlock[] leftVSb = new SubBlock[2];
        for (int x = 0; x < 2; ++x) {
            aboveUSb[x] = aboveMb.getUSubBlock(x, 1);
            leftUSb[x] = leftMb.getUSubBlock(1, x);
            aboveVSb[x] = aboveMb.getVSubBlock(x, 1);
            leftVSb[x] = leftMb.getVSubBlock(1, x);
        }
        for (int b = 0; b < 2; ++b) {
            for (int a = 0; a < 4; ++a) {
                for (int d = 0; d < 2; ++d) {
                    for (int c = 0; c < 4; ++c) {
                        int upred = leftUSb[b].getDest()[3][a] + aboveUSb[d].getDest()[c][3] - alu;
                        upred = squeeze(upred);
                        this.uSubBlocks[d][b].setPixel(c, a, upred);
                        int vpred = leftVSb[b].getDest()[3][a] + aboveVSb[d].getDest()[c][3] - alv;
                        vpred = squeeze(vpred);
                        this.vSubBlocks[d][b].setPixel(c, a, vpred);
                    }
                }
            }
        }
    }

    private void predictY(final Frame frame) {
        switch (this.yMode) {
            case 0: {
                this.handleDCPREDLookup(frame);
                break;
            }
            case 1: {
                this.handleVPREDLookup(frame);
                break;
            }
            case 2: {
                this.handleHPREDLookup(frame);
                break;
            }
            case 3: {
                this.handleTMPREDLookup(frame);
                break;
            }
        }
    }

    private void handleTMPREDLookup(final Frame frame) {
        final MacroBlock aboveMb = frame.getMacroBlock(this.x, this.y - 1);
        final MacroBlock leftMb = frame.getMacroBlock(this.x - 1, this.y);
        final MacroBlock ALMb = frame.getMacroBlock(this.x - 1, this.y - 1);
        final SubBlock ALSb = ALMb.getYSubBlock(3, 3);
        final int al = ALSb.getDest()[3][3];
        final SubBlock[] aboveYSb = new SubBlock[4];
        final SubBlock[] leftYSb = new SubBlock[4];
        for (int x = 0; x < 4; ++x) {
            aboveYSb[x] = aboveMb.getYSubBlock(x, 3);
        }
        for (int x = 0; x < 4; ++x) {
            leftYSb[x] = leftMb.getYSubBlock(3, x);
        }
        for (int b = 0; b < 4; ++b) {
            for (int a = 0; a < 4; ++a) {
                for (int d = 0; d < 4; ++d) {
                    for (int c = 0; c < 4; ++c) {
                        final int pred = leftYSb[b].getDest()[3][a] + aboveYSb[d].getDest()[c][3] - al;
                        this.ySubBlocks[d][b].setPixel(c, a, squeeze(pred));
                    }
                }
            }
        }
    }

    private void handleHPREDLookup(final Frame frame) {
        final MacroBlock leftMb = frame.getMacroBlock(this.x - 1, this.y);
        final SubBlock[] leftYSb = new SubBlock[4];
        for (int x = 0; x < 4; ++x) {
            leftYSb[x] = leftMb.getYSubBlock(3, x);
        }
        for (int y = 0; y < 4; ++y) {
            for (int x = 0; x < 4; ++x) {
                final SubBlock sb = this.ySubBlocks[x][y];
                final int[][] block = new int[4][4];
                for (int j = 0; j < 4; ++j) {
                    for (int i = 0; i < 4; ++i) {
                        block[i][j] = leftYSb[y].getPredict(0, true)[3][j];
                    }
                }
                sb.setPredict(block);
            }
        }
        final SubBlock[] leftUSb = new SubBlock[2];
        for (int x = 0; x < 2; ++x) {
            leftUSb[x] = leftMb.getYSubBlock(1, x);
        }
    }

    private void handleVPREDLookup(final Frame frame) {
        final MacroBlock aboveMb = frame.getMacroBlock(this.x, this.y - 1);
        final SubBlock[] aboveYSb = new SubBlock[4];
        for (int x = 0; x < 4; ++x) {
            aboveYSb[x] = aboveMb.getYSubBlock(x, 3);
        }
        for (int y = 0; y < 4; ++y) {
            for (int x = 0; x < 4; ++x) {
                final SubBlock sb = this.ySubBlocks[x][y];
                final int[][] block = new int[4][4];
                for (int j = 0; j < 4; ++j) {
                    for (int i = 0; i < 4; ++i) {
                        block[i][j] = aboveYSb[x].getPredict(2, false)[i][3];
                    }
                }
                sb.setPredict(block);
            }
        }
    }

    private void handleDCPREDLookup(final Frame frame) {
        final MacroBlock aboveMb = frame.getMacroBlock(this.x, this.y - 1);
        final MacroBlock leftMb = frame.getMacroBlock(this.x - 1, this.y);
        boolean up_available = false;
        boolean left_available = false;
        int average = 0;
        if (this.x > 0) {
            left_available = true;
        }
        if (this.y > 0) {
            up_available = true;
        }
        int expected_dc;
        if (up_available || left_available) {
            if (up_available) {
                for (int j = 0; j < 4; ++j) {
                    final SubBlock sb = aboveMb.getYSubBlock(j, 3);
                    for (int i = 0; i < 4; ++i) {
                        average += sb.getDest()[i][3];
                    }
                }
            }
            if (left_available) {
                for (int j = 0; j < 4; ++j) {
                    final SubBlock sb = leftMb.getYSubBlock(3, j);
                    for (int i = 0; i < 4; ++i) {
                        average += sb.getDest()[3][i];
                    }
                }
            }
            int shift = 3;
            if (up_available) {
                ++shift;
            }
            if (left_available) {
                ++shift;
            }
            expected_dc = average + (1 << shift - 1) >> shift;
        } else {
            expected_dc = 128;
        }
        final int[][] fill = new int[4][4];
        for (int y = 0; y < 4; ++y) {
            for (int x = 0; x < 4; ++x) {
                fill[x][y] = expected_dc;
            }
        }
        for (int y = 0; y < 4; ++y) {
            for (int x = 0; x < 4; ++x) {
                final SubBlock sb2 = this.ySubBlocks[x][y];
                sb2.setPredict(fill);
            }
        }
    }

    private void recon_mb() {
        for (int j = 0; j < 4; ++j) {
            for (int i = 0; i < 4; ++i) {
                final SubBlock sb = this.ySubBlocks[i][j];
                sb.reconstruct();
            }
        }
        for (int j = 0; j < 2; ++j) {
            for (int i = 0; i < 2; ++i) {
                final SubBlock sb = this.uSubBlocks[i][j];
                sb.reconstruct();
            }
        }
        for (int j = 0; j < 2; ++j) {
            for (int i = 0; i < 2; ++i) {
                final SubBlock sb = this.vSubBlocks[i][j];
                sb.reconstruct();
            }
        }
    }

    void setFilterLevel(final int value) {
        this.filterLevel = value;
    }

    void setSkipCoeff(final int mbSkipCoeff) {
        this.skipCoeff = mbSkipCoeff;
    }

    void setUvMode(final int mode) {
        this.uvMode = mode;
    }

    void setYMode(final int yMode) {
        this.yMode = yMode;
    }

    private static int squeeze(final int input) {
        return (input > 255) ? 255 : Math.max(input, 0);
    }
}
