package javafx.iio.plugin.simplewebp.utils;

public class DataByteLittle {
    private int p;
    private final byte[] data;

    public DataByteLittle(final byte[] data) {
        this.data = data;
        this.p = 0;
    }

    public int getU8() {
        return this.data[this.p++] & 0xFF;
    }

    public int getU16() {
        return (this.data[this.p++] & 0xFF) | (this.data[this.p++] & 0xFF) << 8;
    }

    public int getU24() {
        return (this.data[this.p++] & 0xFF) | (this.data[this.p++] & 0xFF) << 8 | (this.data[this.p++] & 0xFF) << 16;
    }

    public int getU32() {
        return (this.data[this.p++] & 0xFF) | (this.data[this.p++] & 0xFF) << 8 | (this.data[this.p++] & 0xFF) << 16 | (this.data[this.p++] & 0xFF) << 24;
    }

    public void read(final byte[] copyTo) {
        System.arraycopy(this.data, this.p, copyTo, 0, copyTo.length);
        this.p += copyTo.length;
    }

    public void skip(final int n) {
        this.p += n;
    }

    public void moveTo(final int p) {
        this.p = p;
    }

    public int getLength() {
        return this.data.length;
    }

    public void close() {
    }

    public int getPosition() {
        return this.p;
    }

    public String getFOURCC() {
        final byte[] bb = new byte[4];
        this.read(bb);
        return new String(bb);
    }
}
