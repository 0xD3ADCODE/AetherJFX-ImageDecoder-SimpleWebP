package javafx.iio.plugin.simplewebp.vp8.data;

class SegmentQuants {
    final SegmentQ[] segQuants;

    private static int getDQ(final BitDecoder bc) {
        int ret = 0;
        if (bc.getBit() > 0) {
            ret = bc.getLiteral(4);
            if (bc.getBit() > 0) {
                ret = -ret;
            }
        }
        return ret;
    }

    SegmentQuants() {
        this.segQuants = new SegmentQ[4];
        for (int x = 0; x < 4; ++x) {
            this.segQuants[x] = new SegmentQ();
        }
    }

    void parse(final BitDecoder bc, final boolean hasSegmentation, final boolean mb_segement_abs_delta) {
        final int index = bc.getLiteral(7);
        final int y1dcdq;
        int v = y1dcdq = getDQ(bc);
        final int y2dcdq;
        v = (y2dcdq = getDQ(bc));
        final int y2acdq;
        v = (y2acdq = getDQ(bc));
        final int uvdcdq;
        v = (uvdcdq = getDQ(bc));
        final int uvacdq;
        v = (uvacdq = getDQ(bc));
        for (final SegmentQ s : this.segQuants) {
            if (!hasSegmentation) {
                s.index = index;
            } else if (!mb_segement_abs_delta) {
                final SegmentQ segmentQ = s;
                segmentQ.index += index;
            }
            s.setY1dc(y1dcdq);
            s.setY2DC(y2dcdq);
            s.setY2ac_delta_q(y2acdq);
            s.setDeltaQUVDC(uvdcdq);
            s.setDeltaQUVAC(uvacdq);
        }
    }
}
