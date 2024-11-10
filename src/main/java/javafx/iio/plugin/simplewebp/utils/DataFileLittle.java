package javafx.iio.plugin.simplewebp.utils;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

public class DataFileLittle {
    private int pos;
    private final RandomAccessFile ra;
    private final int len;
    private final byte[] temp;
    private final int tSize;
    private int ts;
    private int te;

    public DataFileLittle(final File f) throws IOException {
        this.ra = new RandomAccessFile(f, "r");
        this.len = (int) f.length();
        this.tSize = Math.min(8192, this.len);
        this.temp = new byte[this.tSize];
        this.te = this.tSize;
        this.ra.read(this.temp);
    }

    public DataFileLittle(final RandomAccessFile raf) throws IOException {
        this.ra = raf;
        this.len = (int) raf.length();
        this.tSize = Math.min(8192, this.len);
        this.temp = new byte[this.tSize];
        this.te = this.tSize;
        this.ra.read(this.temp);
    }

    public int getU8() throws IOException {
        if (this.pos >= this.ts && this.pos < this.te) {
            final int v = this.temp[this.pos - this.ts] & 0xFF;
            ++this.pos;
            return v;
        }
        this.ts = this.pos;
        this.te = this.ts + this.tSize;
        this.ra.seek(this.pos);
        final int max = Math.min(this.len - this.pos, this.tSize);
        this.ra.read(this.temp, 0, max);
        ++this.pos;
        return this.temp[0] & 0xFF;
    }

    public int getU16() throws IOException {
        return this.getU8() | this.getU8() << 8;
    }

    public int getU24() throws IOException {
        return this.getU8() | this.getU8() << 8 | this.getU8() << 16;
    }

    public int getU32() throws IOException {
        return this.getU8() | this.getU8() << 8 | this.getU8() << 16 | this.getU8() << 24;
    }

    public void read(final byte[] copyTo) throws IOException {
        for (int i = 0, ii = Math.min(copyTo.length, this.len - this.pos); i < ii; ++i) {
            copyTo[i] = (byte) this.getU8();
        }
    }

    public int getPosition() {
        return this.pos;
    }

    public void skip(final int n) {
        this.pos += n;
    }

    public void moveTo(final int p) {
        this.pos = p;
    }

    public int getLength() {
        return this.len;
    }

    public void close() throws IOException {
        this.ra.close();
    }

    public String getFOURCC() throws IOException {
        final byte[] bb = new byte[4];
        this.read(bb);
        return new String(bb);
    }
}
