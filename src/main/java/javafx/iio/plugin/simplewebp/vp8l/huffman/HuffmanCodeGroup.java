/*
 * Copyright (c) 2022, Harald Kuhr
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * * Redistributions of source code must retain the above copyright notice,
 *   this list of conditions and the following disclaimer.
 *
 * * Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * * Neither the name of the copyright holder nor the names of its
 *   contributors may be used to endorse or promote products derived from
 *   this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package javafx.iio.plugin.simplewebp.vp8l.huffman;

import javafx.iio.plugin.simplewebp.utils.LSBBitInputStream;

import java.io.IOException;

/**
 * @author Simon Kammermeier
 */
public final class HuffmanCodeGroup {
    /**
     * Used for green, backward reference length and color cache
     */
    public final HuffmanTable mainCode;

    public final HuffmanTable redCode;
    public final HuffmanTable blueCode;
    public final HuffmanTable alphaCode;
    public final HuffmanTable distanceCode;

    public HuffmanCodeGroup(LSBBitInputStream lsbBitReader, int colorCacheBits) throws IOException {
        mainCode = new HuffmanTable(lsbBitReader, 256 + 24 + (colorCacheBits > 0 ? 1 << colorCacheBits : 0));
        redCode = new HuffmanTable(lsbBitReader, 256);
        blueCode = new HuffmanTable(lsbBitReader, 256);
        alphaCode = new HuffmanTable(lsbBitReader, 256);
        distanceCode = new HuffmanTable(lsbBitReader, 40);
    }
}
