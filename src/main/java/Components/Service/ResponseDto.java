package Components.Service;

public class ResponseDto {
    private String response = null;
    private byte[] data = null;

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }

    public byte[] getData() {
        return data;
    }

    public ResponseDto(String response, byte[] data) {
        this.response = response;
        this.data = data;
    }

    public void setData(byte[] data) {
        this.data = data;
    }

    public ResponseDto(String response) {
        this.response = response;
    }
}
