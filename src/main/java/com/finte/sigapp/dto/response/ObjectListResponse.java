package com.finte.sigapp.dto.response;

import java.util.List;

/**
 * Envoltura estandar de una respuesta con una coleccion.
 *
 * Convencion de {@code code}: 0 proceso exitoso, -1 error.
 */
public class ObjectListResponse {

    private int code;
    private String msg;
    private List<Object> list;

    public ObjectListResponse() {
    }

    public ObjectListResponse(int code) {
        this.code = code;
    }

    public ObjectListResponse(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public ObjectListResponse(int code, String msg, List<Object> list) {
        this.code = code;
        this.msg = msg;
        this.list = list;
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

    public List<Object> getList() {
        return list;
    }

    public void setList(List<Object> list) {
        this.list = list;
    }
}
