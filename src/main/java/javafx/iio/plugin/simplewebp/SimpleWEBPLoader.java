/*
 * Copyright 2023 Burning_TNT
 * Licensed under the Apache License, Version 2.0 (the "License");
 *
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 *
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package javafx.iio.plugin.simplewebp;

import javafx.iio.*;
import javafx.iio.plugin.simplewebp.utils.LSBBitInputStream;
import javafx.iio.plugin.simplewebp.utils.RGBABuffer;
import javafx.iio.plugin.simplewebp.vp8.VP8Decoder;
import javafx.iio.plugin.simplewebp.vp8l.VP8LDecoder;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.List;

public class SimpleWEBPLoader extends IIOLoader {
    private static final String FORMAT_NAME = "WebP";
    private static final List<String> EXTENSIONS = List.of("webp");
    private static final List<IIOSignature> SIGNATURES = List.of(new IIOSignature((byte) 'R', (byte) 'I', (byte) 'F', (byte) 'F'));

    // WEBP Constants
    private static final int RIFF_MAGIC = 'R' << 24 | 'I' << 16 | 'F' << 8 | 'F';
    private static final int WEBP_MAGIC = 'W' << 24 | 'E' << 16 | 'B' << 8 | 'P';
    private static final int CHUNK_VP8L = 'V' << 24 | 'P' << 16 | '8' << 8 | 'L';
    private static final int CHUNK_VP8 = 'V' << 24 | 'P' << 16 | '8' << 8 | ' ';

    public static void register() {
        IIO.registerImageLoader(FORMAT_NAME, EXTENSIONS, SIGNATURES, EXTENSIONS, SimpleWEBPLoader::new);
    }

    private SimpleWEBPLoader(InputStream stream) {
        super(stream);
    }

    @Override
    public IIOImageFrame decode(int imageIndex, int rWidth, int rHeight, boolean preserveAspectRatio, boolean smooth) throws IOException {
        RGBABuffer.AbsoluteRGBABuffer rgbaBuffer = decodeStream();

        int width = rgbaBuffer.getWidth();
        int height = rgbaBuffer.getHeight();

        int[] outWH = IIOImageTools.computeDimensions(width, height, rWidth, rHeight, preserveAspectRatio);
        rWidth = outWH[0];
        rHeight = outWH[1];

        IIOImageFrame imageFrame = new IIOImageFrame(
                IIOImageType.RGBA,
                ByteBuffer.wrap(rgbaBuffer.getRGBAData()),
                width, height,
                width * 4, null,
                new IIOImageMetadata(
                        null, Boolean.TRUE, null, null, null, null, null,
                        rWidth, rHeight,
                        null, null, null
                )
        );

        return width != rWidth || height != rHeight ? IIOImageTools.scaleImageFrame(imageFrame, rWidth, rHeight, smooth) : imageFrame;
    }

    /**
     * Decode the data in the specific inputStream by all the SimpleWEBPLoaders which are supported.
     *
     * @return An absolute RGBA formatted buffer.
     * @throws IOException If the data is not WEBP formatted.
     */
    private RGBABuffer.AbsoluteRGBABuffer decodeStream() throws IOException {
        try (DataInputStream dataInputStream = new DataInputStream(super.stream)) {
            if (dataInputStream.readInt() != RIFF_MAGIC) {
                throw new IOException("Invalid RIFF_MAGIC.");
            }

            dataInputStream.readInt();

            if (dataInputStream.readInt() != WEBP_MAGIC) {
                throw new IOException("Invalid WEBP_MAGIC.");
            }

            return switch (dataInputStream.readInt()) {
                case CHUNK_VP8L -> VP8LDecoder.decode(dataInputStream, new LSBBitInputStream(super.stream));
                case CHUNK_VP8 -> VP8Decoder.decode(super.stream);
                default -> throw new IOException("SimpleWEBP cannot decode such WEBP type.");
            };
        }
    }
}
