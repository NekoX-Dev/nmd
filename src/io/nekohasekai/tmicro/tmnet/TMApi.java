package io.nekohasekai.tmicro.tmnet;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class TMApi {

    private static final int CONSTRUCTOR = Integer.MIN_VALUE;
    public static final int TM_OK = CONSTRUCTOR;
    public static final int TM_ERROR = CONSTRUCTOR + 1;
    public static final int TM_RPC_REQUEST = CONSTRUCTOR + 2;
    public static final int TM_RPC_RESPONSE = CONSTRUCTOR + 3;
    public static final int TM_INIT_CONNECTION = CONSTRUCTOR + 4;
    public static final int TM_INIT_TEMP = CONSTRUCTOR + 5;
    public static final int TM_INIT_VERIFY = CONSTRUCTOR + 6;
    public static final int TM_CLIENT_INFO = CONSTRUCTOR + 7;
    public static final int TM_GET_INFO = CONSTRUCTOR + 8;

    public static final int LAYER = 0;

    public static abstract class Object {

        public abstract int getConstructor();

        public abstract void readParams(AbstractSerializedData stream, boolean exception);

        public abstract void serializeToStream(AbstractSerializedData stream);

        public int getObjectSize() {
            SerializedData byteBuffer = new SerializedData(true);
            serializeToStream(byteBuffer);
            return byteBuffer.length();
        }

        public static Gson gson;

        @Override
        public String toString() {
            if (gson == null) {
                gson = new GsonBuilder()
                        .setPrettyPrinting()
                        .create();
            }
            return getClass().getSimpleName() + " " + gson.toJson(this);
        }
    }

    public static class Ok extends Object {

        public int getConstructor() {
            return TM_OK;
        }

        public void readParams(AbstractSerializedData stream, boolean exception) {
        }

        public void serializeToStream(AbstractSerializedData stream) {
        }

    }

    public static class Error extends Object {

        public int code;
        public String message;

        public int getConstructor() {
            return TM_ERROR;
        }

        public void readParams(AbstractSerializedData stream, boolean exception) {
            code = stream.readInt32(exception);
            message = stream.readString(exception);
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(code);
            stream.writeString(message);
        }

    }

    public static abstract class Function extends Object {
    }

    public static class RpcRequest extends Function {

        public int requestId;
        public Function request;

        public int getConstructor() {
            return TM_RPC_REQUEST;
        }

        public void readParams(AbstractSerializedData stream, boolean exception) {
            requestId = stream.readInt32(exception);
            request = (Function) TMClassStore.deserializeFromSteam(stream, exception);
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(requestId);
            TMClassStore.serializeToStream(stream, request);
        }
    }

    public static class RpcResponse extends Object {

        public int requestId;
        public Object response;

        public int getConstructor() {
            return TM_RPC_RESPONSE;
        }

        public void readParams(AbstractSerializedData stream, boolean exception) {
            requestId = stream.readInt32(exception);
            response = TMClassStore.deserializeFromSteam(stream, exception);
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(requestId);
            TMClassStore.serializeToStream(stream, response);
        }

    }

    public static class InitConnection extends Function {

        public int layer;
        public int appVersion;
        public String platform;
        public String systemVersion;
        public byte[] session;
        public Object query;

        public InitConnection() {
        }

        public InitConnection(int layer, int appVersion, String platform, String systemVersion, Object query) {
            this.layer = layer;
            this.appVersion = appVersion;
            this.platform = platform;
            this.systemVersion = systemVersion;
            this.query = query;
        }

        public int getConstructor() {
            return TM_INIT_CONNECTION;
        }

        public void readParams(AbstractSerializedData stream, boolean exception) {
            layer = stream.readInt32(exception);
            appVersion = stream.readInt32(exception);
            platform = stream.readString(exception);
            systemVersion = stream.readString(exception);
            session = stream.readByteArray(exception);
            int queryConstructor = stream.readInt32(exception);
            if (queryConstructor != Integer.MAX_VALUE) {
                query = TMClassStore.deserializeFromSteam(stream, exception);
            }
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(layer);
            stream.writeInt32(appVersion);
            stream.writeString(platform);
            stream.writeString(systemVersion);
            stream.writeByteArray(session);
            if (query != null) {
                TMClassStore.serializeToStream(stream, query);
            } else {
                stream.writeInt32(Integer.MAX_VALUE);
            }
        }
    }

    public static class ConnInitTemp extends Object {

        public byte[] data;

        public int getConstructor() {
            return TM_INIT_TEMP;
        }

        public void readParams(AbstractSerializedData stream, boolean exception) {
            data = stream.readByteArray(exception);
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeByteArray(data);
        }

    }

    public static class VerifyConnection extends Function {

        public byte[] data;

        public int getConstructor() {
            return TM_INIT_VERIFY;
        }

        public void readParams(AbstractSerializedData stream, boolean exception) {
            data = stream.readByteArray(exception);
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeByteArray(data);
        }

    }

    public static final int STATUS_WAIT_PHONE = 0;
    public static final int STATUS_WAIT_CODE = 1;
    public static final int STATUS_WAIT_PSWD = 2;
    public static final int STATUS_AUTHED = 3;

    public static class ClientInfo extends Object {

        public int accountStatus;
        public int loginUser;

        public int getConstructor() {
            return TM_CLIENT_INFO;
        }

        public void readParams(AbstractSerializedData stream, boolean exception) {
            accountStatus = stream.readInt32(exception);
            if (accountStatus == STATUS_AUTHED) {
                loginUser = stream.readInt32(exception);
            }
        }

        public void serializeToStream(AbstractSerializedData stream) {
            stream.writeInt32(accountStatus);
            if (accountStatus == STATUS_AUTHED) {
                stream.writeInt32(loginUser);
            }
        }
    }

    public static class GetInfo extends Function {

        public int getConstructor() {
            return TM_GET_INFO;
        }

        public void readParams(AbstractSerializedData stream, boolean exception) {
        }

        public void serializeToStream(AbstractSerializedData stream) {
        }

    }


}