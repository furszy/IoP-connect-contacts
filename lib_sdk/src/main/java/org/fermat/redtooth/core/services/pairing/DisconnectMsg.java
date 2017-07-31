package org.fermat.redtooth.core.services.pairing;

import org.fermat.redtooth.global.utils.SerializationUtils;
import org.fermat.redtooth.profile_server.engine.app_services.BaseMsg;

/**
 * Created by German on 26/07/2017.
 */

public class DisconnectMsg extends BaseMsg<DisconnectMsg> {


    public DisconnectMsg() {
    }

    @Override
    public byte[] encode() throws Exception {
        return SerializationUtils.serialize(this);
    }

    @Override
    public DisconnectMsg decode(byte[] msg) throws Exception {
        return SerializationUtils.deserialize(msg,DisconnectMsg.class);
    }

    @Override
    public String getType() {
        return PairingMsgTypes.PAIR_DISCONNECT.getType();
    }
}
