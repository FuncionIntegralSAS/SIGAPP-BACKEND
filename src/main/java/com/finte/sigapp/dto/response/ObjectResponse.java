package com.finte.sigapp.dto.response;

/**
 * Envoltura estandar de una respuesta con un unico elemento.
 *
 * Reemplaza los Map.of("success", ...) que se venian devolviendo desde los
 * controllers.
 *
 * Convencion de {@code code}: 0 proceso exitoso, -1 error.
 */
public class ObjectResponse {

    private int code;
    private String msg;
    private Object object;

    public ObjectResponse() {
    }

    public ObjectResponse(int code) {
        this.code = code;
    }

    public ObjectResponse(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public ObjectResponse(int code, String msg, Object object) {
        this.code = code;
        this.msg = msg;
        this.object = object;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public Object getObject() {
        return object;
    }

    public void setObject(Object object) {
        this.object = object;
    }
}
