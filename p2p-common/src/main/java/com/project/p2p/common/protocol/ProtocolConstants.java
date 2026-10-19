package com.project.p2p.common.protocol;

/**
 * Binary wire-protocol constants for P2P encrypted chat framing.
 *
 * Frame Format (24-byte Header + Variable Payload, Network Byte Order / Big-Endian):
 *  0                   1                   2                   3
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                     Magic Bytes ('P2P1')                      |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |    Version    | Message Type  |             Flags             |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                                                               |
 * +                     Sequence Number (64-bit)                  +
 * |                                                               |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                        Payload Length                         |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                       Header CRC32                            |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                     Payload (Variable length)                 |
 * |                              ...                              |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *
 * Note: CRC32 is calculated over header bytes 0..19 (Magic through Payload Length).
 * CRC32 provides early detection of accidental transmission errors and bit-flips;
 * it is NOT a cryptographic security or authentication mechanism.
 */
public final class ProtocolConstants {

    private ProtocolConstants() {}

    /** Magic 4-byte preamble: 'P2P1' (0x50 0x32 0x50 0x31) */
    public static final int MAGIC_BYTES = 0x50325031;

    /** Protocol version 1 */
    public static final byte CURRENT_VERSION = 0x01;

    /** Fixed frame header size in bytes */
    public static final int HEADER_SIZE = 24;

    /** Default maximum allowed frame payload size (16 MB) */
    public static final int DEFAULT_MAX_PAYLOAD_SIZE = 16 * 1024 * 1024;

    /** Maximum allowed accumulator buffer size (32 MB) to prevent unbounded memory allocation */
    public static final int MAX_ACCUMULATOR_BUFFER_SIZE = 32 * 1024 * 1024;

    /** Default TCP port for direct peer listening */
    public static final int DEFAULT_PEER_PORT = 9001;

    /** Standard socket connect timeout in milliseconds */
    public static final int CONNECT_TIMEOUT_MS = 10000;

    /** Socket read timeout in milliseconds (0 = infinite / keepalive) */
    public static final int DEFAULT_SO_TIMEOUT_MS = 30000;
}
