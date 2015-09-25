package com.example.rogrio.bluett;

/**
 * Created by Rogerio on 01/09/2015.
 */


public class Crc16 {

    private int m_uCrc16;

    private byte[] m_buffer;

    public void MBS_crc16_init()
    {
        m_uCrc16 = 0x0000;//0xFFFF; //XMODEM CRC

        m_buffer = new byte[256];

        for (int i = 0; i < m_buffer.length; i++) {
            m_buffer[i] = 0;
        }
    }

    public void MBS_crc16_update(byte ucByte)
    {
        int polynomial = 0x1021;   // 0001 0000 0010 0001  (0, 5, 12)

        for (int i = 0; i < 8; i++) {
            boolean bit = ((ucByte   >> (7-i) & 1) == 1);
            boolean c15 = ((m_uCrc16 >> 15    & 1) == 1);
            m_uCrc16 <<= 1;
            if (c15 ^ bit) m_uCrc16 ^= polynomial;
        }

    }

    public void MBS_crc16_final()
    {
        m_uCrc16 &= 0xffff;
    }

    public byte MBS_crc16_get_MSB()
    {
        return (byte)((m_uCrc16 >> 8) & 0x00FF);
    }

    public byte MBS_crc16_get_LSB()
    {
        return (byte)((m_uCrc16) & 0x00FF);
    }

}
