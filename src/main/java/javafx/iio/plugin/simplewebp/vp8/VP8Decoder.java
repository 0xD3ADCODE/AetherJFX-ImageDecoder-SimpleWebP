package javafx.iio.plugin.simplewebp.vp8;

import javafx.iio.plugin.simplewebp.utils.DataByteLittle;
import javafx.iio.plugin.simplewebp.utils.RGBABuffer;
import javafx.iio.plugin.simplewebp.vp8.data.Frame;

import java.io.IOException;
import java.io.InputStream;

public class VP8Decoder {
    private static final byte[] HEADER = {82, 73, 70, 70, 24, 99, 2, 0, 87, 69, 66, 80, 86, 80, 56, 32};

    public static RGBABuffer.AbsoluteRGBABuffer decode(InputStream inputStream) throws IOException {
        byte[] data = readBytes(inputStream);
        DataByteLittle reader = new DataByteLittle(data);
        String ss = reader.getFOURCC();
        if (!"RIFF".equals(ss)) {
            throw new IOException("Not a valid WEBP file : RIFF header not found");
        }

        int fileSize = reader.getU32();
        ss = reader.getFOURCC();
        if (!"WEBP".equals(ss)) {
            throw new IOException("Not a valid WEBP file : WEBP header not found - filesize" + fileSize);
        }

        int maxRead = data.length - 8;
        while (reader.getPosition() < maxRead) {
            ss = reader.getFOURCC();
            int chunkSize = reader.getU32();
            switch (ss) {
                case "VP8 ": {
                    return new Frame(reader).getRGBABuffer();
                }
                case "VP8L": {
                    throw new IOException("WEBP Lossless Format is not yet supported");
                }
                case "ANMF": {
                    reader.skip(16);
                    continue;
                }
                default: {
                    reader.skip(chunkSize);
                    if (chunkSize % 2 == 1) {
                        reader.skip(1);
                    }
                }
            }
        }
        return null;
    }

    private static byte[] readBytes(InputStream inputStream) throws IOException {
        byte[] dataStream = inputStream.readAllBytes();
        try {
            inputStream.close();
        } catch (Exception ignored) {
        }

        byte[] data = new byte[HEADER.length + dataStream.length];

        System.arraycopy(HEADER, 0, data, 0, HEADER.length);
        System.arraycopy(dataStream, 0, data, HEADER.length, dataStream.length);

        return data;
    }
}
