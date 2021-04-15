package io.nekohasekai.tmicro.tmnet;

public class TMStore {

    public static void serializeToStream(AbstractSerializedData stream, TMApi.Object object) {
        if (object == null) {
            stream.writeInt32(0x0);
        } else {
            stream.writeInt32(object.getConstructor());
            if (object instanceof TMApi.Function) {
                stream.writeInt32(((TMApi.Function) object).requestId);
            }
            object.serializeToStream(stream);
        }
    }

    public static TMApi.Object deserializeFromSteam(AbstractSerializedData stream, boolean exception, TMApi.Object instance) {
        instance.readParams(stream, exception);
        return instance;
    }

    public static TMApi.Object deserializeFromSteam(AbstractSerializedData stream, boolean exception) {
        int constructor = stream.readInt32(exception);
        return deserializeFromSteam(stream, exception, constructor);
    }

    public static TMApi.Object deserializeFromSteam(AbstractSerializedData stream, boolean exception, int constructor) {
        if (constructor == 0x0) return null;
        TMApi.Object response;
        switch (constructor) {
            case 0x1:
                response = new TMApi.Ok();
                break;
            case 0x2:
                response = new TMApi.Error();
                break;
            case 0x3:
                response = new TMApi.Response();
                break;
            case 0x4:
                response = new TMApi.InitConnection();
                break;
            case 0x5:
                response = new TMApi.ConnInitTemp();
                break;
            case 0x6:
                response = new TMApi.VerifyConnection();
                break;
            case 0x7:
                response = new TMApi.AuthenticationCodeTypeTelegramMessage();
                break;
            case 0x8:
                response = new TMApi.AuthenticationCodeTypeSms();
                break;
            case 0x9:
                response = new TMApi.AuthenticationCodeTypeCall();
                break;
            case 0xa:
                response = new TMApi.AuthenticationCodeInfo();
                break;
            case 0xb:
                response = new TMApi.EmailAddressAuthenticationCodeInfo();
                break;
            case 0xc:
                response = new TMApi.AuthorizationStateWaitPhoneNumber();
                break;
            case 0xd:
                response = new TMApi.AuthorizationStateWaitCode();
                break;
            case 0xe:
                response = new TMApi.AuthorizationStateWaitRegistration();
                break;
            case 0xf:
                response = new TMApi.AuthorizationStateWaitPassword();
                break;
            case 0x10:
                response = new TMApi.AuthorizationStateReady();
                break;
            case 0x11:
                response = new TMApi.AuthorizationStateLoggingOut();
                break;
            case 0x12:
                response = new TMApi.UpdateAuthorizationState();
                break;
            default:
                throw new IllegalStateException("Unknown constructor" + constructor);
        }
        if (response instanceof TMApi.Function) {
            ((TMApi.Function) response).requestId = stream.readInt32(exception);
        }
        response.readParams(stream, exception);
        return response;
    }

}