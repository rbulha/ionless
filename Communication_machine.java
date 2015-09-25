package com.example.rogrio.bluett;

/**
 * Created by Rogerio on 01/09/2015.
 *
 * ref: http://techheap.packetizer.com/communication/modems/xmodem.html
 *
 */

enum EACTION {
    A_NONE,
    A_ENTER,
    A_SELECT,
    A_ESC,
    A_UP,
    A_DOWN,
    A_COMM_INIT,
    A_COMM_SET_HOUR,
    A_COMM_SET_MIN,
    A_COMM_SET_SEC,
    A_COMM_SET_DAY,
    A_COMM_SET_MON,
    A_COMM_SET_YEAR,
    A_COMM_SET_WEEK,
    A_COMM_CHECK_PACKET_HEADER_SOH,
    A_COMM_CHECK_PACKET_HEADER_PACK_NUM,
    A_COMM_CHECK_PACKET_HEADER_PACK_NUM_COMP,
    A_COMM_CHECK_PACKET_RCVING,
    A_COMM_SEND_PACKET_HEADER,
    A_COMM_SEND_PACKET_SEQ,
    A_COMM_SEND_PACKET_W_ACK,
    A_COMM_GENERATE_DIAG,
    A_COMM_CHECK_PACKET_CRC,
    A_LAST
}

enum ESTATE {
    E_MENU_IDLE,
    E_MENU_CONFIG,
    E_MENU_ROT_1,
    E_MENU_ROT_2,
    E_MENU_ROT_3,
    E_MENU_ROT_4,
    E_MENU_ROT_5,
    E_MENU_ROT_6,
    E_READ_TIME,
    E_OPER_STATE,
    E_RX_UART,
    E_TX_UART,
    E_CONFIG_TIME,
    E_CONFIG_HOUR,
    E_CONFIG_MIN,
    E_CONFIG_SEC,
    E_CONFIG_DAY,
    E_CONFIG_MON,
    E_CONFIG_YEA,
    E_CONFIG_WEEK,
    E_COMM_IDLE,
    E_COMM_SET,
    E_COMM_GET,
    E_COMM_GET_FILE,
    E_DIAG_INIT,
    E_SET_CONDUCTIV,
    E_COMM_SET_FILE,
    E_LAST_STATE
}

class machine{
    ESTATE  estate;
    EACTION eaction;
    int  uiBufferIndex;
    int  uiChunkIndex;
    int  ucPacketIndex;
    int  ucID;
    int  g_ucReceiveBuffer;
    int  SBUF1;
    int  uiCRC_rcv;
}

public class Communication_machine {
    private machine mm_machine;

    private byte[] g_aucFileBuffer = new byte[20512];

    private Crc16 mmCrc16;

    public Communication_machine() {
        this.mm_machine = new machine();
        this.mm_machine.estate          = ESTATE.E_COMM_SET_FILE; //ESTATE.E_COMM_SET_FILE; //
        this.mm_machine.eaction         = EACTION.A_COMM_INIT; //A_COMM_CHECK_PACKET_HEADER_SOH; //
        this.mm_machine.uiBufferIndex   = 0;
        this.mm_machine.ucPacketIndex   = 0x01;
        this.mm_machine.ucID            = (int)0xAC;
        mmCrc16 = new Crc16();
    }

    public int communication_task(int ucInput)
    {
        this.mm_machine.SBUF1 = 0;
        this.mm_machine.g_ucReceiveBuffer = ucInput;
        comm_exec_state_machine();
        return this.mm_machine.SBUF1;
    }

    public int comm_exec_state_machine()
    {
        switch( this.mm_machine.estate){
            case E_COMM_IDLE :
                //return comm_idle_state();
            case E_COMM_SET_FILE:
                return comm_file_set_state();
        }
        return 0;
    }

    private int comm_idle_state()
    {
        switch(this.mm_machine.eaction)
        {
            case A_NONE:
                switch (this.mm_machine.g_ucReceiveBuffer)
                {
                    case 'o' :
                        this.mm_machine.eaction          = EACTION.A_COMM_INIT;
                        this.mm_machine.estate           = ESTATE.E_COMM_SET_FILE;
                        this.mm_machine.uiBufferIndex    = 0;
                        this.mm_machine.ucPacketIndex    = 0x01;
                        break;

                    default : //vai direto para recepaoo de arquivos
                        this.mm_machine.uiBufferIndex    = 0;
                        this.mm_machine.ucPacketIndex    = 0x01;
                        this.mm_machine.estate          = ESTATE.E_COMM_SET_FILE; //ESTATE.E_COMM_SET_FILE;
                        this.mm_machine.eaction         = EACTION.A_COMM_CHECK_PACKET_HEADER_SOH; //A_COMM_INIT;
                        comm_file_set_state();
                }
                break;
        }
        return 0;
    }

    private int comm_file_set_state()
    {
        switch(this.mm_machine.eaction)
        {
            case A_COMM_INIT:
                switch (this.mm_machine.g_ucReceiveBuffer)
                {
                    case 'o' :
                        this.mm_machine.uiBufferIndex    = 0;
                        this.mm_machine.ucPacketIndex    = 0x01;
                        this.mm_machine.eaction          = EACTION.A_COMM_CHECK_PACKET_HEADER_SOH;
                        this.mm_machine.estate           = ESTATE.E_COMM_SET_FILE;
                        this.mm_machine.SBUF1 = 0x43;
                        break;
                }
                break;
            case A_COMM_CHECK_PACKET_HEADER_SOH:
                switch (this.mm_machine.g_ucReceiveBuffer)
                {
                    case 0x01 : //SOH
                        this.mm_machine.eaction          = EACTION.A_COMM_CHECK_PACKET_HEADER_PACK_NUM;
                        this.mm_machine.estate           = ESTATE.E_COMM_SET_FILE;
                        break;
                    case 0x04 : //EOT
                        this.mm_machine.eaction          = EACTION.A_NONE;
                        this.mm_machine.estate           = ESTATE.E_COMM_IDLE;
                        this.mm_machine.SBUF1 = 0x04; //ACK  end of tranmission.      
                        break;
                    default:
                        this.mm_machine.eaction          = EACTION.A_NONE;
                        this.mm_machine.estate           = ESTATE.E_COMM_IDLE;
                        this.mm_machine.SBUF1 = 0x15; //NACK        
                }
                break;
            case A_COMM_CHECK_PACKET_HEADER_PACK_NUM:
                if (this.mm_machine.g_ucReceiveBuffer == this.mm_machine.ucPacketIndex)
                {
                    this.mm_machine.eaction          = EACTION.A_COMM_CHECK_PACKET_HEADER_PACK_NUM_COMP;
                    this.mm_machine.estate           = ESTATE.E_COMM_SET_FILE;
                }
                else
                {
                    this.mm_machine.eaction          = EACTION.A_NONE;
                    this.mm_machine.estate           = ESTATE.E_COMM_IDLE;
                    this.mm_machine.SBUF1 = 0x15; //NACK        
                }
                break;
            case A_COMM_CHECK_PACKET_HEADER_PACK_NUM_COMP:
                if (this.mm_machine.g_ucReceiveBuffer == (255 - this.mm_machine.ucPacketIndex))
                {
                    this.mm_machine.eaction          = EACTION.A_COMM_CHECK_PACKET_RCVING;
                    this.mm_machine.estate           = ESTATE.E_COMM_SET_FILE;
                    mmCrc16.MBS_crc16_init();
                }
                else
                {
                    this.mm_machine.eaction          = EACTION.A_NONE;
                    this.mm_machine.estate           = ESTATE.E_COMM_IDLE;
                    this.mm_machine.SBUF1 = 0x15; //NACK        
                }
                break;
            case A_COMM_CHECK_PACKET_RCVING:
                if (this.mm_machine.uiChunkIndex < 128 )
                {
                    this.mm_machine.eaction          = EACTION.A_COMM_CHECK_PACKET_RCVING;
                    this.mm_machine.estate           = ESTATE.E_COMM_SET_FILE;
                    g_aucFileBuffer[this.mm_machine.uiBufferIndex] = (byte)this.mm_machine.g_ucReceiveBuffer;
                    mmCrc16.MBS_crc16_update((byte)this.mm_machine.g_ucReceiveBuffer);
                    this.mm_machine.uiChunkIndex++;
                    this.mm_machine.uiBufferIndex++;
                }
                else
                {
                    this.mm_machine.uiChunkIndex = 0;
                    mmCrc16.MBS_crc16_final();
                    if((byte)this.mm_machine.g_ucReceiveBuffer == mmCrc16.MBS_crc16_get_MSB())
                    {
                        this.mm_machine.uiCRC_rcv = (0x0000 | this.mm_machine.g_ucReceiveBuffer) << 8; //CRC MSB
                        this.mm_machine.eaction          = EACTION.A_COMM_CHECK_PACKET_CRC;
                        this.mm_machine.estate           = ESTATE.E_COMM_SET_FILE;
                    }
                    else
                    {
                        this.mm_machine.eaction          = EACTION.A_NONE;
                        this.mm_machine.estate           = ESTATE.E_COMM_IDLE;
                        this.mm_machine.SBUF1 = 0x15; //NACK        
                    }
                }
                break;
            case A_COMM_CHECK_PACKET_CRC:
                if((byte)this.mm_machine.g_ucReceiveBuffer == mmCrc16.MBS_crc16_get_LSB())
                {
                    this.mm_machine.uiCRC_rcv = this.mm_machine.uiCRC_rcv | this.mm_machine.g_ucReceiveBuffer; //CRC LSB

                    this.mm_machine.ucPacketIndex++;
                    this.mm_machine.eaction          = EACTION.A_COMM_CHECK_PACKET_HEADER_SOH;
                    this.mm_machine.estate           = ESTATE.E_COMM_SET_FILE;

                    this.mm_machine.SBUF1 = 0x06; //ACK
                }
                else
                {
                    this.mm_machine.eaction          = EACTION.A_NONE;
                    this.mm_machine.estate           = ESTATE.E_COMM_IDLE;
                    this.mm_machine.SBUF1 = 0x15; //NACK        
                }
                break;
        }
        return 1;
    }

    public byte[] comm_save_file() {
        return g_aucFileBuffer;
    }
}

