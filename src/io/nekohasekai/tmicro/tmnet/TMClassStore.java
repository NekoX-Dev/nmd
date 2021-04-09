package io.nekohasekai.tmicro.tmnet;

import io.nekohasekai.tmicro.tmnet.TMApi.*;

public class TMClassStore {

    public static void serializeToStream(AbstractSerializedData stream, TMApi.Object object) {
        stream.writeInt32(object.getConstructor());
        object.serializeToStream(stream);
    }

    public static TMApi.Object deserializeFromSteam(AbstractSerializedData stream, boolean exception) {
        int constructor = stream.readInt32(exception);
        return deserializeFromSteam(stream, exception, constructor);
    }

    public static TMApi.Object deserializeFromSteam(AbstractSerializedData stream, boolean exception, int constructor) {
        TMApi.Object response;
        switch (constructor) {
            case TMApi.TM_OK:
                response = new TMApi.Ok();
                break;
            case TMApi.TM_ERROR:
                response = new TMApi.Error();
                break;
            case TMApi.TM_RPC_REQUEST:
                response = new RpcRequest();
                break;
            case TMApi.TM_RPC_RESPONSE:
                response = new RpcResponse();
                break;
            case TMApi.TM_INIT_CONNECTION:
                response = new InitConnection();
                break;
            case TMApi.TM_INIT_TEMP:
                response = new ConnInitTemp();
                break;
            case TMApi.TM_INIT_VERIFY:
                response = new VerifyConnection();
                break;
            default:
                throw new IllegalStateException("Unknown constructor" + constructor);
        }
        response.readParams(stream, exception);
        return response;
    }

}
