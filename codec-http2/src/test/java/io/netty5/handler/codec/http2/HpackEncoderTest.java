/*
 * Copyright 2017 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty5.handler.codec.http2;

import io.netty5.buffer.api.Buffer;
import io.netty5.handler.codec.http2.headers.Http2Headers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import static io.netty5.buffer.api.DefaultBufferAllocators.onHeapAllocator;
import static io.netty5.handler.codec.http2.Http2CodecUtil.DEFAULT_HEADER_LIST_SIZE;
import static io.netty5.handler.codec.http2.Http2CodecUtil.MAX_HEADER_TABLE_SIZE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

public class HpackEncoderTest {
    private HpackDecoder hpackDecoder;
    private HpackEncoder hpackEncoder;
    private Http2Headers mockHeaders;

    @BeforeEach
    public void setUp() {
        hpackEncoder = new HpackEncoder();
        hpackDecoder = new HpackDecoder(DEFAULT_HEADER_LIST_SIZE);
        mockHeaders = mock(Http2Headers.class);
    }

    @Test
    public void testSetMaxHeaderTableSizeToMaxValue() throws Http2Exception {
        try (Buffer buf = onHeapAllocator().allocate(256)) {
            hpackEncoder.setMaxHeaderTableSize(buf, MAX_HEADER_TABLE_SIZE);
            hpackDecoder.setMaxHeaderTableSize(MAX_HEADER_TABLE_SIZE);
            hpackDecoder.decode(0, buf, mockHeaders, true);
            assertEquals(MAX_HEADER_TABLE_SIZE, hpackDecoder.getMaxHeaderTableSize());
        }
    }

    @Test
    public void testSetMaxHeaderTableSizeOverflow() throws Http2Exception {
        try (Buffer buf = onHeapAllocator().allocate(256)) {
            assertThrows(Http2Exception.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    hpackEncoder.setMaxHeaderTableSize(buf, MAX_HEADER_TABLE_SIZE + 1);
                }
            });
        }
    }

    /**
     * The encoder should not impose an arbitrary limit on the header size if
     * the server has not specified any limit.
     */
    @Test
    public void testWillEncode16MBHeaderByDefault() throws Http2Exception {
        try (Buffer buf = onHeapAllocator().allocate(256)) {
            String bigHeaderName = "x-big-header";
            int bigHeaderSize = 1024 * 1024 * 16;
            String bigHeaderVal = new String(new char[bigHeaderSize]).replace('\0', 'X');
            Http2Headers headersIn = Http2Headers.newHeaders().add(
                    "x-big-header", bigHeaderVal);
            Http2Headers headersOut = Http2Headers.newHeaders();

            hpackEncoder.encodeHeaders(0, buf, headersIn, Http2HeadersEncoder.NEVER_SENSITIVE);
            hpackDecoder.setMaxHeaderListSize(bigHeaderSize + 1024);
            hpackDecoder.decode(0, buf, headersOut, false);
            assertEquals(headersOut.get(bigHeaderName).toString(), bigHeaderVal);
        }
    }

    @Test
    public void testSetMaxHeaderListSizeEnforcedAfterSet() throws Http2Exception {
        try (Buffer buf = onHeapAllocator().allocate(256)) {
            final Http2Headers headers = Http2Headers.newHeaders().add(
                    "x-big-header",
                    new String(new char[1024 * 16]).replace('\0', 'X')
            );

            hpackEncoder.setMaxHeaderListSize(1000);

            assertThrows(Http2Exception.class, new Executable() {
                @Override
                public void execute() throws Throwable {
                    hpackEncoder.encodeHeaders(0, buf, headers, Http2HeadersEncoder.NEVER_SENSITIVE);
                }
            });
        }
    }
}