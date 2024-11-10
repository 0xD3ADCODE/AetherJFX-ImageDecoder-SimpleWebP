package javafx.iio.plugin.simplewebp.vp8.data;

import javafx.iio.plugin.simplewebp.utils.DataByteLittle;
import javafx.iio.plugin.simplewebp.utils.RGBABuffer;

import java.util.ArrayList;
import java.util.List;

public class Frame {
    private final int[][][][] coefProbs;
    private int filterLevel;
    private int filterType;
    private int frameType;
    private final DataByteLittle reader;
    private int height;
    private int mbCols;
    private int macroBlockNoCoeffSkip;
    private int mbRows;
    private MacroBlock[][] macroBlocks;
    private int macroBlockSegementAbsoluteDelta;
    private int[] macroBlockSegmentTreeProbs;
    private final int[] modeLoopFilterDeltas;
    private int modeRefLoopFilterDeltaEnabled;
    private int multiTokenPartition;
    private int offset;
    private final int[] refLoopFilterDeltas;
    private int segmentationIsEnabled;
    private SegmentQuants segmentQuants;
    private int sharpnessLevel;
    private BitDecoder tokenBoolDecoder;
    private final List<BitDecoder> tokenBitDecoders;
    private int mBlockMap;
    private int width;

    public Frame(final DataByteLittle stream) {
        this.modeLoopFilterDeltas = new int[4];
        this.refLoopFilterDeltas = new int[4];
        this.reader = stream;
        this.offset = this.reader.getPosition();
        this.coefProbs = LookUp.getClonedDCP();
        this.tokenBitDecoders = new ArrayList<>();
    }

    public RGBABuffer.AbsoluteRGBABuffer getRGBABuffer() {
        decodeFrame();

        RGBABuffer.AbsoluteRGBABuffer buffer = RGBABuffer.createAbsoluteImage(this.width, this.height);

        for (int y = 0; y < this.height; ++y) {
            final int j = (y >> 1 & 0x7) >> 2;
            final int yPoint = y >> 1 & 0x3;
            for (int x = 0; x < this.width; ++x) {
                final int i = (x >> 1 & 0x7) >> 2;
                final int xPoint = x >> 1 & 0x3;
                final MacroBlock macroBlock = this.getMacroBlock(x >> 4, y >> 4);
                final int yy = macroBlock.getSubBlock(SubBlock.Layer.Y1, (x & 0xF) >> 2, (y & 0xF) >> 2).getDest()[x & 0x3][y & 0x3];
                int u = macroBlock.getSubBlock(SubBlock.Layer.U, i, j).getDest()[xPoint][yPoint];
                int v = macroBlock.getSubBlock(SubBlock.Layer.V, i, j).getDest()[xPoint][yPoint];
                u -= 128;
                v -= 128;
                final int a0 = 1192 * (yy - 16);
                final int a2 = 1634 * v;
                final int a3 = 832 * v;
                final int a4 = 400 * u;
                final int a5 = 2066 * u;
                int r = a0 + a2 >> 10;
                int g = a0 - a3 - a4 >> 10;
                int b = a0 + a5 >> 10;
                r = ((r > 255) ? 255 : ((r < 0) ? 0 : r));
                g = ((g > 255) ? 255 : ((g < 0) ? 0 : g));
                b = ((b > 255) ? 255 : ((b < 0) ? 0 : b));

                byte[] rgba = new byte[4];
                rgba[0] = (byte) r;
                rgba[1] = (byte) g;
                rgba[2] = (byte) b;
                rgba[3] = (byte) 255;
                buffer.setDataElements(x, y, rgba);
            }
        }

        return buffer;
    }

    private void createMacroBlocks() {
        this.macroBlocks = new MacroBlock[this.mbCols + 2][this.mbRows + 2];
        final int xx = this.mbCols + 2;
        final int yy = this.mbRows + 2;
        final int[] cache16 = new int[16];
        for (int x = 0; x < xx; ++x) {
            for (int y = 0; y < yy; ++y) {
                this.macroBlocks[x][y] = new MacroBlock(x, y, cache16);
            }
        }
    }

    public void decodeFrame() {
        this.segmentQuants = new SegmentQuants();
        this.reader.moveTo(this.offset++);
        int c = this.reader.getU8();
        this.frameType = getBitAsInt(c, 0);
        if (this.frameType != 0) {
            return;
        }
        int firstPartitionLengthInBytes = getBitAsInt(c, 5);
        firstPartitionLengthInBytes += getBitAsInt(c, 6) << 1;
        firstPartitionLengthInBytes += getBitAsInt(c, 7) << 2;
        c = this.reader.getU8();
        firstPartitionLengthInBytes += c << 3;
        c = this.reader.getU8();
        firstPartitionLengthInBytes += c << 11;
        this.reader.getU8();
        this.reader.getU8();
        this.reader.getU8();
        c = this.reader.getU8();
        this.offset += 6;
        int hBytes = c;
        this.reader.moveTo(this.offset++);
        c = this.reader.getU8();
        hBytes += c << 8;
        this.width = (hBytes & 0x3FFF);
        this.reader.moveTo(this.offset++);
        int vBytes;
        c = (vBytes = this.reader.getU8());
        this.reader.moveTo(this.offset++);
        c = this.reader.getU8();
        vBytes += c << 8;
        this.height = (vBytes & 0x3FFF);
        int tWidth = this.width;
        int tHeight = this.height;
        if ((tWidth & 0xF) != 0x0) {
            tWidth += 16 - (tWidth & 0xF);
        }
        if ((tHeight & 0xF) != 0x0) {
            tHeight += 16 - (tHeight & 0xF);
        }
        this.mbRows = tHeight >> 4;
        this.mbCols = tWidth >> 4;
        this.createMacroBlocks();
        final BitDecoder bc = new BitDecoder(this.reader, this.offset);
        if (this.frameType == 0) {
            bc.getLiteral(2);
        }
        this.segmentationIsEnabled = bc.getBit();
        if (this.segmentationIsEnabled > 0) {
            this.mBlockMap = bc.getBit();
            final int mBlockData = bc.getBit();
            if (mBlockData > 0) {
                this.processmBlockData(bc);
            }
        }
        final int simpleFilter = bc.getBit();
        this.filterLevel = bc.getLiteral(6);
        this.sharpnessLevel = bc.getLiteral(3);
        this.modeRefLoopFilterDeltaEnabled = bc.getBit();
        if (this.modeRefLoopFilterDeltaEnabled > 0) {
            this.updateModeRefLoopFilter(bc);
        }
        this.filterType = ((this.filterLevel == 0) ? 0 : ((simpleFilter > 0) ? 1 : 2));
        this.setupTokenDecoder(bc, firstPartitionLengthInBytes, this.offset);
        bc.seek();
        this.segmentQuants.parse(bc, this.segmentationIsEnabled == 1, this.macroBlockSegementAbsoluteDelta == 1);
        bc.getBit();
        if (this.frameType != 0) {
            bc.getBit();
        }
        this.decodeFrameStep2(bc);
    }

    private void updateModeRefLoopFilter(final BitDecoder bc) {
        final int modeRefLoopFilterDeltaUpdate = bc.getBit();
        if (modeRefLoopFilterDeltaUpdate > 0) {
            for (int i = 0; i < 4; ++i) {
                if (bc.getBit() > 0) {
                    this.refLoopFilterDeltas[i] = bc.getLiteral(6);
                    if (bc.getBit() > 0) {
                        final int[] refLoopFilterDeltas = this.refLoopFilterDeltas;
                        final int n = i;
                        refLoopFilterDeltas[n] *= -1;
                    }
                }
            }
            for (int i = 0; i < 4; ++i) {
                if (bc.getBit() > 0) {
                    this.modeLoopFilterDeltas[i] = bc.getLiteral(6);
                    if (bc.getBit() > 0) {
                        final int[] modeLoopFilterDeltas = this.modeLoopFilterDeltas;
                        final int n2 = i;
                        modeLoopFilterDeltas[n2] *= -1;
                    }
                }
            }
        }
    }

    private void decodeFrameStep2(final BitDecoder bc) {
        for (int i = 0; i < 4; ++i) {
            for (int j = 0; j < 8; ++j) {
                for (int k = 0; k < 3; ++k) {
                    for (int l = 0; l < 11; ++l) {
                        if (bc.getProbBit(LookUp.PROB_COS[i][j][k][l]) > 0) {
                            this.coefProbs[i][j][k][l] = bc.getLiteral(8);
                        }
                    }
                }
            }
        }
        this.macroBlockNoCoeffSkip = bc.getBit();
        if (this.frameType == 0) {
            this.readModes(bc);
        }
        int ibc = 0;
        final int num_part = 1 << this.multiTokenPartition;
        for (int mb_row = 0; mb_row < this.mbRows; ++mb_row) {
            if (num_part > 1) {
                (this.tokenBoolDecoder = this.tokenBitDecoders.get(ibc)).seek();
                this.decodeMacroBlockRow(mb_row);
                if (++ibc == num_part) {
                    ibc = 0;
                }
            } else {
                this.decodeMacroBlockRow(mb_row);
            }
        }
        if (this.filterType > 0 && this.filterLevel != 0) {
            filterFrame(this);
        }
    }

    private void processmBlockData(final BitDecoder bc) {
        this.macroBlockSegementAbsoluteDelta = bc.getBit();
        for (int i = 0; i < 4; ++i) {
            int value = 0;
            if (bc.getBit() > 0) {
                value = bc.getLiteral(LookUp.BITS_MACRO[0]);
                if (bc.getBit() > 0) {
                    value = -value;
                }
            }
            this.segmentQuants.segQuants[i].index = value;
        }
        for (int i = 0; i < 4; ++i) {
            int value = 0;
            if (bc.getBit() > 0) {
                value = bc.getLiteral(LookUp.BITS_MACRO[1]);
                if (bc.getBit() > 0) {
                    value = -value;
                }
            }
            this.segmentQuants.segQuants[i].strength = value;
        }
        if (this.mBlockMap > 0) {
            this.macroBlockSegmentTreeProbs = new int[3];
            for (int i = 0; i < 3; ++i) {
                int value;
                if (bc.getBit() > 0) {
                    value = bc.getLiteral(8);
                } else {
                    value = 255;
                }
                this.macroBlockSegmentTreeProbs[i] = value;
            }
        }
    }

    private void decodeMacroBlockRow(final int mbRow) {
        for (int mbCol = 0; mbCol < this.mbCols; ++mbCol) {
            final MacroBlock mb = this.getMacroBlock(mbCol, mbRow);
            mb.decodeMacroBlock(this);
            mb.dequantMacroBlock(this);
        }
    }

    public SubBlock getTopRightSubBlock(final SubBlock sb, final SubBlock.Layer plane) {
        final MacroBlock mb = sb.macroBlock;
        final int x = mb.getSubblockX(sb);
        final int y = mb.getSubblockY(sb);
        if (plane != SubBlock.Layer.Y1) {
            throw new IllegalArgumentException("bad input: getAboveRightSubBlock()");
        }
        if (y == 0 && x < 3) {
            final MacroBlock mb2 = this.getMacroBlock(mb.getX(), mb.getY() - 1);
            final SubBlock r = mb2.getSubBlock(SubBlock.Layer.Y1, x + 1, 3);
            return r;
        }
        if (y == 0 && x == 3) {
            final MacroBlock mb2 = this.getMacroBlock(mb.getX() + 1, mb.getY() - 1);
            SubBlock r = mb2.getSubBlock(SubBlock.Layer.Y1, 0, 3);
            if (mb2.getX() == this.mbCols) {
                final int[][] dest = new int[4][4];
                for (int b = 0; b < 4; ++b) {
                    for (int a = 0; a < 4; ++a) {
                        if (mb2.getY() < 0) {
                            dest[a][b] = 127;
                        } else {
                            dest[a][b] = this.getMacroBlock(mb.getX(), mb.getY() - 1).getSubBlock(SubBlock.Layer.Y1, 3, 3).getDest()[3][3];
                        }
                    }
                }
                r = new SubBlock(mb2, null, null, SubBlock.Layer.Y1);
                r.dest = dest;
            }
            return r;
        }
        if (y > 0 && x < 3) {
            final SubBlock r = mb.getSubBlock(SubBlock.Layer.Y1, x + 1, y - 1);
            return r;
        }
        final SubBlock sb2 = mb.getSubBlock(sb.getLayer(), 3, 0);
        return this.getTopRightSubBlock(sb2, SubBlock.Layer.Y1);
    }

    public SubBlock getTopSubBlock(final SubBlock sb, final SubBlock.Layer plane) {
        SubBlock r = sb.getAbove();
        if (r == null) {
            final MacroBlock mb = sb.macroBlock;
            final int x = mb.getSubblockX(sb);
            MacroBlock mb2;
            for (mb2 = this.getMacroBlock(mb.getX(), mb.getY() - 1); plane == SubBlock.Layer.Y2 && mb2.getYMode() == 4; mb2 = this.getMacroBlock(mb2.getX(), mb2.getY() - 1)) {
            }
            r = mb2.getBottomSubBlock(x, sb.getLayer());
        }
        return r;
    }

    private static int getBitAsInt(final int data, final int bit) {
        final int r = data & 1 << bit;
        if (r > 0) {
            return 1;
        }
        return 0;
    }

    public int[][][][] getCoefProbs() {
        return this.coefProbs;
    }

    public SubBlock getLeftSubBlock(final SubBlock sb, final SubBlock.Layer plane) {
        SubBlock r = sb.getLeft();
        if (r == null) {
            final MacroBlock mb = sb.macroBlock;
            final int y = mb.getSubblockY(sb);
            MacroBlock mb2;
            for (mb2 = this.getMacroBlock(mb.getX() - 1, mb.getY()); plane == SubBlock.Layer.Y2 && mb2.getYMode() == 4; mb2 = this.getMacroBlock(mb2.getX() - 1, mb2.getY())) {
            }
            r = mb2.getRightSubBlock(y, sb.getLayer());
        }
        return r;
    }

    public MacroBlock getMacroBlock(final int mbCol, final int mbRow) {
        return this.macroBlocks[mbCol + 1][mbRow + 1];
    }

    public SegmentQuants getSegmentQuants() {
        return this.segmentQuants;
    }

    public BitDecoder getTokenBoolDecoder() {
        this.tokenBoolDecoder.seek();
        return this.tokenBoolDecoder;
    }

    private void readModes(final BitDecoder bc) {
        int mb_row = -1;
        int prob_skip_false = 0;
        if (this.macroBlockNoCoeffSkip > 0) {
            prob_skip_false = bc.getLiteral(8);
        }
        while (++mb_row < this.mbRows) {
            int mb_col = -1;
            while (++mb_col < this.mbCols) {
                final MacroBlock mb = this.getMacroBlock(mb_col, mb_row);
                if (this.segmentationIsEnabled > 0 && this.mBlockMap > 0) {
                    mb.key = bc.getTree(LookUp.MB_SEG_TREE, this.macroBlockSegmentTreeProbs);
                }
                if (this.modeRefLoopFilterDeltaEnabled > 0) {
                    int level = this.filterLevel;
                    level += this.refLoopFilterDeltas[0];
                    level = ((level < 0) ? 0 : ((level > 63) ? 63 : level));
                    mb.setFilterLevel(level);
                } else {
                    mb.setFilterLevel(this.segmentQuants.segQuants[mb.key].strength);
                }
                int mb_skip_coeff;
                if (this.macroBlockNoCoeffSkip > 0) {
                    mb_skip_coeff = bc.getProbBit(prob_skip_false);
                } else {
                    mb_skip_coeff = 0;
                }
                mb.setSkipCoeff(mb_skip_coeff);
                final int y_mode = readYMode(bc);
                mb.setYMode(y_mode);
                if (y_mode == 4) {
                    this.handleB_PRED(bc, mb);
                } else {
                    handleOther(mb, y_mode);
                }
                final int mode = readUvMode(bc);
                mb.setUvMode(mode);
            }
        }
    }

    private static void handleOther(final MacroBlock mb, final int y_mode) {
        int BMode;
        switch (y_mode) {
            case 1: {
                BMode = 2;
                break;
            }
            case 2: {
                BMode = 3;
                break;
            }
            case 3: {
                BMode = 1;
                break;
            }
            default: {
                BMode = 0;
                break;
            }
        }
        for (int x = 0; x < 4; ++x) {
            for (int y = 0; y < 4; ++y) {
                final SubBlock sb = mb.getYSubBlock(x, y);
                sb.setMode(BMode);
            }
        }
    }

    private void handleB_PRED(final BitDecoder bc, final MacroBlock mb) {
        for (int i = 0; i < 4; ++i) {
            for (int j = 0; j < 4; ++j) {
                final SubBlock sb = mb.getYSubBlock(j, i);
                final SubBlock top = this.getTopSubBlock(sb, SubBlock.Layer.Y1);
                final SubBlock left = this.getLeftSubBlock(sb, SubBlock.Layer.Y1);
                final int mode = readSubBlockMode(bc, top.mode, left.mode);
                sb.setMode(mode);
            }
        }
        if (this.modeRefLoopFilterDeltaEnabled > 0) {
            int level = mb.getFilterLevel();
            level += this.modeLoopFilterDeltas[0];
            level = ((level < 0) ? 0 : ((level > 63) ? 63 : level));
            mb.setFilterLevel(level);
        }
    }

    private int readPartitionSize(final int l) {
        this.reader.moveTo(l);
        return this.reader.getU8() + (this.reader.getU8() << 8) + (this.reader.getU8() << 16);
    }

    private static int readSubBlockMode(final BitDecoder bc, final int A, final int L) {
        return bc.getTree(LookUp.SUBBLOCK_MODE_TREE, LookUp.FRAMES_SUBBLOCK[A][L]);
    }

    private static int readUvMode(final BitDecoder bc) {
        return bc.getTree(LookUp.UV_MODE_TREE, LookUp.UV_FRAME_PROB);
    }

    private static int readYMode(final BitDecoder bc) {
        return bc.getTree(LookUp.Y_FRAME_TREE, LookUp.FRAME_YMODE_PROB);
    }

    private void setupTokenDecoder(final BitDecoder bc, final int first_partition_length_in_bytes, final int offset) {
        int partition;
        final int partStart = partition = offset + first_partition_length_in_bytes;
        this.multiTokenPartition = bc.getLiteral(2);
        final int num_part = 1 << this.multiTokenPartition;
        if (num_part > 1) {
            partition += 3 * (num_part - 1);
        }
        for (int i = 0; i < num_part; ++i) {
            int partSize;
            if (i < num_part - 1) {
                partSize = this.readPartitionSize(partStart + i * 3);
                bc.seek();
            } else {
                partSize = this.reader.getLength() - partition;
            }
            this.tokenBitDecoders.add(new BitDecoder(this.reader, partition));
            partition += partSize;
        }
        this.tokenBoolDecoder = this.tokenBitDecoders.get(0);
    }

    private static int common_adjust(final boolean use_outer_taps, final Segment seg) {
        final int p1 = getSigned(seg.P1);
        final int p2 = getSigned(seg.P0);
        final int q0 = getSigned(seg.Q0);
        final int q2 = getSigned(seg.Q1);
        int a = sClamp((use_outer_taps ? sClamp(p1 - q2) : 0) + 3 * (q0 - p2));
        final int b = sClamp(a + 3) >> 3;
        a = sClamp(a + 4) >> 3;
        seg.Q0 = getUnsigned(q0 - a);
        seg.P0 = getUnsigned(p2 + b);
        return a;
    }

    private static boolean doY(final int I, final int E, final int p3, final int p2, final int p1, final int p0, final int q0, final int q1, final int q2, final int q3) {
        return abs(p0 - q0) * 2 + abs(p1 - q1) / 2 <= E && abs(p3 - p2) <= I && abs(p2 - p1) <= I && abs(p1 - p0) <= I && abs(q3 - q2) <= I && abs(q2 - q1) <= I && abs(q1 - q0) <= I;
    }

    private static Segment getSegH(final SubBlock rsb, final SubBlock lsb, final int a) {
        final Segment seg = new Segment();
        final int[][] rdest = rsb.getDest();
        final int[][] ldest = lsb.getDest();
        seg.P0 = ldest[3][a];
        seg.P1 = ldest[2][a];
        seg.P2 = ldest[1][a];
        seg.P3 = ldest[0][a];
        seg.Q0 = rdest[0][a];
        seg.Q1 = rdest[1][a];
        seg.Q2 = rdest[2][a];
        seg.Q3 = rdest[3][a];
        return seg;
    }

    private static Segment getSegV(final SubBlock bsb, final SubBlock tsb, final int a) {
        final Segment seg = new Segment();
        final int[][] bdest = bsb.getDest();
        final int[][] tdest = tsb.getDest();
        seg.P0 = tdest[a][3];
        seg.P1 = tdest[a][2];
        seg.P2 = tdest[a][1];
        seg.P3 = tdest[a][0];
        seg.Q0 = bdest[a][0];
        seg.Q1 = bdest[a][1];
        seg.Q2 = bdest[a][2];
        seg.Q3 = bdest[a][3];
        return seg;
    }

    private static boolean hev(final int threshold, final int p1, final int p0, final int q0, final int q1) {
        return abs(p1 - p0) > threshold || abs(q1 - q0) > threshold;
    }

    private static void filterFrame(final Frame frame) {
        if (frame.filterType == 2) {
            filterUV(frame);
            filterY(frame);
        } else if (frame.filterType == 1) {
            filterNorm(frame);
        }
    }

    private static void filterNorm(final Frame frame) {
        for (int y = 0; y < frame.mbRows; ++y) {
            for (int x = 0; x < frame.mbCols; ++x) {
                final MacroBlock rmb = frame.getMacroBlock(x, y);
                final MacroBlock bmb = frame.getMacroBlock(x, y);
                final int loop_filter_level = rmb.getFilterLevel();
                if (loop_filter_level != 0) {
                    int iLimit = rmb.getFilterLevel();
                    final int sharpnessLevel = frame.sharpnessLevel;
                    if (sharpnessLevel > 0) {
                        iLimit >>= ((sharpnessLevel > 4) ? 2 : 1);
                        if (iLimit > 9 - sharpnessLevel) {
                            iLimit = 9 - sharpnessLevel;
                        }
                    }
                    if (iLimit == 0) {
                        iLimit = 1;
                    }
                    int sub_bedge_limit = (loop_filter_level << 1) + iLimit;
                    if (sub_bedge_limit < 1) {
                        sub_bedge_limit = 1;
                    }
                    if (x > 0) {
                        setSegHIfXPositive(frame, x, y, rmb, sub_bedge_limit);
                    }
                    if (!rmb.isSkip_inner_lf()) {
                        setSegHIfNoLoopSkip(rmb, sub_bedge_limit);
                    }
                    if (y > 0) {
                        setSegVIfYPositive(frame, x, y, bmb, sub_bedge_limit);
                    }
                    if (!rmb.isSkip_inner_lf()) {
                        setSegVIfNoLoopSkip(bmb, sub_bedge_limit);
                    }
                }
            }
        }
    }

    private static void setSegVIfNoLoopSkip(final MacroBlock bmb, final int sub_bedge_limit) {
        for (int a = 1; a < 4; ++a) {
            for (int b = 0; b < 4; ++b) {
                final SubBlock tsb = bmb.getSubBlock(SubBlock.Layer.Y1, b, a - 1);
                final SubBlock bsb = bmb.getSubBlock(SubBlock.Layer.Y1, b, a);
                for (int c = 0; c < 4; ++c) {
                    final Segment seg = getSegV(bsb, tsb, c);
                    normalizeSegment(sub_bedge_limit, seg);
                    setSegV(bsb, tsb, seg, c);
                }
            }
        }
    }

    private static void setSegVIfYPositive(final Frame frame, final int x, final int y, final MacroBlock bmb, final int sub_bedge_limit) {
        final int mbedge_limit = sub_bedge_limit + 4;
        final MacroBlock tmb = frame.getMacroBlock(x, y - 1);
        for (int b = 0; b < 4; ++b) {
            final SubBlock tsb = tmb.getSubBlock(SubBlock.Layer.Y1, b, 3);
            final SubBlock bsb = bmb.getSubBlock(SubBlock.Layer.Y1, b, 0);
            for (int a = 0; a < 4; ++a) {
                final Segment seg = getSegV(bsb, tsb, a);
                normalizeSegment(mbedge_limit, seg);
                setSegV(bsb, tsb, seg, a);
            }
        }
    }

    private static void setSegHIfNoLoopSkip(final MacroBlock rmb, final int sub_bedge_limit) {
        for (int a = 1; a < 4; ++a) {
            for (int b = 0; b < 4; ++b) {
                final SubBlock lsb = rmb.getSubBlock(SubBlock.Layer.Y1, a - 1, b);
                final SubBlock rsb = rmb.getSubBlock(SubBlock.Layer.Y1, a, b);
                for (int c = 0; c < 4; ++c) {
                    final Segment seg = getSegH(rsb, lsb, c);
                    normalizeSegment(sub_bedge_limit, seg);
                    setSegH(rsb, lsb, seg, c);
                }
            }
        }
    }

    private static void setSegHIfXPositive(final Frame frame, final int x, final int y, final MacroBlock rmb, final int sub_bedge_limit) {
        final int mbedge_limit = sub_bedge_limit + 4;
        final MacroBlock lmb = frame.getMacroBlock(x - 1, y);
        for (int b = 0; b < 4; ++b) {
            final SubBlock rsb = rmb.getSubBlock(SubBlock.Layer.Y1, 0, b);
            final SubBlock lsb = lmb.getSubBlock(SubBlock.Layer.Y1, 3, b);
            for (int a = 0; a < 4; ++a) {
                final Segment seg = getSegH(rsb, lsb, a);
                normalizeSegment(mbedge_limit, seg);
                setSegH(rsb, lsb, seg, a);
            }
        }
    }

    private static void filterUV(final Frame frame) {
        for (int y = 0; y < frame.mbRows; ++y) {
            for (int x = 0; x < frame.mbCols; ++x) {
                final MacroBlock rmb = frame.getMacroBlock(x, y);
                final int level = rmb.getFilterLevel();
                if (level != 0) {
                    if (x > 0) {
                        filterFirst(frame, x, y, rmb, level);
                    }
                    if (!rmb.isSkip_inner_lf()) {
                        filterSecond(frame, rmb, level);
                    }
                    if (y > 0) {
                        filterThird(frame, x, y, rmb, level);
                    }
                    if (!rmb.isSkip_inner_lf()) {
                        filterFourth(frame, x, y, rmb, level);
                    }
                }
            }
        }
    }

    private static void filterFirst(final Frame frame, final int x, final int y, final MacroBlock rmb, final int level) {
        final MacroBlock lmb = frame.getMacroBlock(x - 1, y);
        final int sLevel = frame.sharpnessLevel;
        final int iLimit = getiLimit(rmb, sLevel);
        final int limitMBE = (level + 2 << 1) + iLimit;
        final int hev_threshold = getHev_threshold(frame, level);
        for (int b = 0; b < 2; ++b) {
            final SubBlock rsbU = rmb.getSubBlock(SubBlock.Layer.U, 0, b);
            final SubBlock lsbU = lmb.getSubBlock(SubBlock.Layer.U, 1, b);
            final SubBlock rsbV = rmb.getSubBlock(SubBlock.Layer.V, 0, b);
            final SubBlock lsbV = lmb.getSubBlock(SubBlock.Layer.V, 1, b);
            for (int a = 0; a < 4; ++a) {
                Segment seg = getSegH(rsbU, lsbU, a);
                filterMB(hev_threshold, iLimit, limitMBE, seg);
                setSegH(rsbU, lsbU, seg, a);
                seg = getSegH(rsbV, lsbV, a);
                filterMB(hev_threshold, iLimit, limitMBE, seg);
                setSegH(rsbV, lsbV, seg, a);
            }
        }
    }

    private static void filterSecond(final Frame frame, final MacroBlock rmb, final int level) {
        final int hev_threshold = getHev_threshold(frame, level);
        final int sLevel = frame.sharpnessLevel;
        final int iLimit = getiLimit(rmb, sLevel);
        final int limitSBE = (level << 1) + iLimit;
        for (int b = 0; b < 2; ++b) {
            final SubBlock lsbU = rmb.getSubBlock(SubBlock.Layer.U, 0, b);
            final SubBlock rsbU = rmb.getSubBlock(SubBlock.Layer.U, 1, b);
            final SubBlock lsbV = rmb.getSubBlock(SubBlock.Layer.V, 0, b);
            final SubBlock rsbV = rmb.getSubBlock(SubBlock.Layer.V, 1, b);
            for (int c = 0; c < 4; ++c) {
                Segment seg = getSegH(rsbU, lsbU, c);
                filterSB(hev_threshold, iLimit, limitSBE, seg);
                setSegH(rsbU, lsbU, seg, c);
                seg = getSegH(rsbV, lsbV, c);
                filterSB(hev_threshold, iLimit, limitSBE, seg);
                setSegH(rsbV, lsbV, seg, c);
            }
        }
    }

    private static void filterThird(final Frame frame, final int x, final int y, final MacroBlock rmb, final int level) {
        final MacroBlock tmb = frame.getMacroBlock(x, y - 1);
        final MacroBlock bmb = frame.getMacroBlock(x, y);
        final int sLevel = frame.sharpnessLevel;
        final int iLimit = getiLimit(rmb, sLevel);
        final int limitMBE = (level + 2 << 1) + iLimit;
        final int hev_threshold = getHev_threshold(frame, level);
        for (int b = 0; b < 2; ++b) {
            final SubBlock tsbU = tmb.getSubBlock(SubBlock.Layer.U, b, 1);
            final SubBlock bsbU = bmb.getSubBlock(SubBlock.Layer.U, b, 0);
            final SubBlock tsbV = tmb.getSubBlock(SubBlock.Layer.V, b, 1);
            final SubBlock bsbV = bmb.getSubBlock(SubBlock.Layer.V, b, 0);
            for (int a = 0; a < 4; ++a) {
                Segment seg = getSegV(bsbU, tsbU, a);
                filterMB(hev_threshold, iLimit, limitMBE, seg);
                setSegV(bsbU, tsbU, seg, a);
                seg = getSegV(bsbV, tsbV, a);
                filterMB(hev_threshold, iLimit, limitMBE, seg);
                setSegV(bsbV, tsbV, seg, a);
            }
        }
    }

    private static void filterFourth(final Frame frame, final int x, final int y, final MacroBlock rmb, final int level) {
        final MacroBlock bmb = frame.getMacroBlock(x, y);
        final int sLevel = frame.sharpnessLevel;
        final int hev_threshold = getHev_threshold(frame, level);
        final int iLimit = getiLimit(rmb, sLevel);
        final int limitSBE = (level << 1) + iLimit;
        for (int a = 1; a < 2; ++a) {
            for (int b = 0; b < 2; ++b) {
                final SubBlock tsbU = bmb.getSubBlock(SubBlock.Layer.U, b, 0);
                final SubBlock bsbU = bmb.getSubBlock(SubBlock.Layer.U, b, a);
                final SubBlock tsbV = bmb.getSubBlock(SubBlock.Layer.V, b, 0);
                final SubBlock bsbV = bmb.getSubBlock(SubBlock.Layer.V, b, a);
                for (int c = 0; c < 4; ++c) {
                    Segment seg = getSegV(bsbU, tsbU, c);
                    filterSB(hev_threshold, iLimit, limitSBE, seg);
                    setSegV(bsbU, tsbU, seg, c);
                    seg = getSegV(bsbV, tsbV, c);
                    filterSB(hev_threshold, iLimit, limitSBE, seg);
                    setSegV(bsbV, tsbV, seg, c);
                }
            }
        }
    }

    private static int getiLimit(final MacroBlock rmb, final int sLevel) {
        int iLimit = rmb.getFilterLevel();
        if (sLevel > 0) {
            iLimit >>= ((sLevel > 4) ? 2 : 1);
            if (iLimit > 9 - sLevel) {
                iLimit = 9 - sLevel;
            }
        }
        if (iLimit == 0) {
            iLimit = 1;
        }
        return iLimit;
    }

    private static void filterY(final Frame frame) {
        for (int y = 0; y < frame.mbRows; ++y) {
            for (int x = 0; x < frame.mbCols; ++x) {
                final MacroBlock rmb = frame.getMacroBlock(x, y);
                final int level = rmb.getFilterLevel();
                if (level != 0) {
                    if (x > 0) {
                        filterYFirst(frame, x, y, rmb, level);
                    }
                    if (!rmb.isSkip_inner_lf()) {
                        filterYSecond(frame, rmb, level);
                    }
                    if (y > 0) {
                        filterYThird(frame, x, y, rmb, level);
                    }
                    if (!rmb.isSkip_inner_lf()) {
                        filterYFourth(frame, x, y, rmb, level);
                    }
                }
            }
        }
    }

    private static void filterYFirst(final Frame frame, final int x, final int y, final MacroBlock rmb, final int level) {
        final int hev_threshold = getHev_threshold(frame, level);
        final int sharpnessLevel = frame.sharpnessLevel;
        final int iLimit = getiLimit(rmb, sharpnessLevel);
        final MacroBlock lmb = frame.getMacroBlock(x - 1, y);
        final int mbedge_limit = (level + 2 << 1) + iLimit;
        for (int b = 0; b < 4; ++b) {
            final SubBlock rsb = rmb.getSubBlock(SubBlock.Layer.Y1, 0, b);
            final SubBlock lsb = lmb.getSubBlock(SubBlock.Layer.Y1, 3, b);
            for (int a = 0; a < 4; ++a) {
                final Segment seg = getSegH(rsb, lsb, a);
                filterMB(hev_threshold, iLimit, mbedge_limit, seg);
                setSegH(rsb, lsb, seg, a);
            }
        }
    }

    private static void filterYSecond(final Frame frame, final MacroBlock rmb, final int level) {
        final int hev_threshold = getHev_threshold(frame, level);
        final int sharpnessLevel = frame.sharpnessLevel;
        final int iLimit = getiLimit(rmb, sharpnessLevel);
        final int sub_bedge_limit = (level << 1) + iLimit;
        for (int a = 1; a < 4; ++a) {
            for (int b = 0; b < 4; ++b) {
                final SubBlock lsb = rmb.getSubBlock(SubBlock.Layer.Y1, a - 1, b);
                final SubBlock rsb = rmb.getSubBlock(SubBlock.Layer.Y1, a, b);
                for (int c = 0; c < 4; ++c) {
                    final Segment seg = getSegH(rsb, lsb, c);
                    filterSB(hev_threshold, iLimit, sub_bedge_limit, seg);
                    setSegH(rsb, lsb, seg, c);
                }
            }
        }
    }

    private static void filterYThird(final Frame frame, final int x, final int y, final MacroBlock rmb, final int level) {
        final MacroBlock tmb = frame.getMacroBlock(x, y - 1);
        final MacroBlock bmb = frame.getMacroBlock(x, y);
        final int sharpnessLevel = frame.sharpnessLevel;
        final int hev_threshold = getHev_threshold(frame, level);
        final int iLimit = getiLimit(rmb, sharpnessLevel);
        final int mbedge_limit = (level + 2 << 1) + iLimit;
        for (int b = 0; b < 4; ++b) {
            final SubBlock tsb = tmb.getSubBlock(SubBlock.Layer.Y1, b, 3);
            final SubBlock bsb = bmb.getSubBlock(SubBlock.Layer.Y1, b, 0);
            for (int a = 0; a < 4; ++a) {
                final Segment seg = getSegV(bsb, tsb, a);
                filterMB(hev_threshold, iLimit, mbedge_limit, seg);
                setSegV(bsb, tsb, seg, a);
            }
        }
    }

    private static void filterYFourth(final Frame frame, final int x, final int y, final MacroBlock rmb, final int level) {
        final MacroBlock bmb = frame.getMacroBlock(x, y);
        final int sharpnessLevel = frame.sharpnessLevel;
        final int hev_threshold = getHev_threshold(frame, level);
        final int iLimit = getiLimit(rmb, sharpnessLevel);
        final int sub_bedge_limit = (level << 1) + iLimit;
        for (int a = 1; a < 4; ++a) {
            for (int b = 0; b < 4; ++b) {
                final SubBlock tsb = bmb.getSubBlock(SubBlock.Layer.Y1, b, a - 1);
                final SubBlock bsb = bmb.getSubBlock(SubBlock.Layer.Y1, b, a);
                for (int c = 0; c < 4; ++c) {
                    final Segment seg = getSegV(bsb, tsb, c);
                    filterSB(hev_threshold, iLimit, sub_bedge_limit, seg);
                    setSegV(bsb, tsb, seg, c);
                }
            }
        }
    }

    private static int getHev_threshold(final Frame frame, final int level) {
        int hev_threshold = 0;
        if (frame.frameType == 0) {
            if (level >= 40) {
                hev_threshold = 2;
            } else if (level >= 15) {
                hev_threshold = 1;
            }
        } else if (level >= 40) {
            hev_threshold = 3;
        } else if (level >= 20) {
            hev_threshold = 2;
        } else if (level >= 15) {
            hev_threshold = 1;
        }
        return hev_threshold;
    }

    private static void filterMB(final int hev_threshold, final int iLimit, final int eLimit, final Segment seg) {
        final int p3 = getSigned(seg.P3);
        final int p4 = getSigned(seg.P2);
        final int p5 = getSigned(seg.P1);
        final int p6 = getSigned(seg.P0);
        final int q0 = getSigned(seg.Q0);
        final int q2 = getSigned(seg.Q1);
        final int q3 = getSigned(seg.Q2);
        final int q4 = getSigned(seg.Q3);
        if (doY(iLimit, eLimit, q4, q3, q2, q0, p6, p5, p4, p3)) {
            if (!hev(hev_threshold, p5, p6, q0, q2)) {
                final int w = sClamp(sClamp(p5 - q2) + 3 * (q0 - p6));
                int a = 27 * w + 63 >> 7;
                seg.Q0 = getUnsigned(q0 - a);
                seg.P0 = getUnsigned(p6 + a);
                a = 18 * w + 63 >> 7;
                seg.Q1 = getUnsigned(q2 - a);
                seg.P1 = getUnsigned(p5 + a);
                a = 9 * w + 63 >> 7;
                seg.Q2 = getUnsigned(q3 - a);
                seg.P2 = getUnsigned(p4 + a);
            } else {
                common_adjust(true, seg);
            }
        }
    }

    private static void setSegH(final SubBlock rsb, final SubBlock lsb, final Segment seg, final int a) {
        final int[][] rdest = rsb.getDest();
        final int[][] ldest = lsb.getDest();
        ldest[3][a] = seg.P0;
        ldest[2][a] = seg.P1;
        ldest[1][a] = seg.P2;
        ldest[0][a] = seg.P3;
        rdest[0][a] = seg.Q0;
        rdest[1][a] = seg.Q1;
        rdest[2][a] = seg.Q2;
        rdest[3][a] = seg.Q3;
    }

    private static void setSegV(final SubBlock bsb, final SubBlock tsb, final Segment seg, final int a) {
        final int[][] bdest = bsb.getDest();
        final int[][] tdest = tsb.getDest();
        tdest[a][3] = seg.P0;
        tdest[a][2] = seg.P1;
        tdest[a][1] = seg.P2;
        tdest[a][0] = seg.P3;
        bdest[a][0] = seg.Q0;
        bdest[a][1] = seg.Q1;
        bdest[a][2] = seg.Q2;
        bdest[a][3] = seg.Q3;
    }

    private static void normalizeSegment(final int eLimit, final Segment seg) {
        if (abs(seg.P0 - seg.Q0) * 2 + abs(seg.P1 - seg.Q1) / 2 <= eLimit) {
            common_adjust(true, seg);
        }
    }

    private static void filterSB(final int hev_threshold, final int interior_limit, final int edge_limit, final Segment seg) {
        final int p3 = getSigned(seg.P3);
        final int p4 = getSigned(seg.P2);
        final int p5 = getSigned(seg.P1);
        final int p6 = getSigned(seg.P0);
        final int q0 = getSigned(seg.Q0);
        final int q2 = getSigned(seg.Q1);
        final int q3 = getSigned(seg.Q2);
        final int q4 = getSigned(seg.Q3);
        if (doY(interior_limit, edge_limit, q4, q3, q2, q0, p6, p5, p4, p3)) {
            final boolean hv = hev(hev_threshold, p5, p6, q0, q2);
            final int a = common_adjust(hv, seg) + 1 >> 1;
            if (!hv) {
                seg.Q1 = getUnsigned(q2 - a);
                seg.P1 = getUnsigned(p5 + a);
            }
        }
    }

    private static int getSigned(final int v) {
        return v - 128;
    }

    private static int getUnsigned(final int v) {
        return sClamp(v) + 128;
    }

    private static int abs(final int v) {
        return (v < 0) ? (-v) : v;
    }

    private static int sClamp(final int v) {
        int r = v;
        if (v < -128) {
            r = -128;
        }
        if (v > 127) {
            r = 127;
        }
        return r;
    }

    private static class Segment {
        int P0;
        int P1;
        int P2;
        int P3;
        int Q0;
        int Q1;
        int Q2;
        int Q3;
    }
}
