package com.amos.analysisprojecttool.bean;


import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
public class ComRes<T> {
    private int status = 200;
    private String msg = "OK";
    private T data;

    public static <T> ComRes success(T body) {
        ComRes res = new ComRes();
        res.setData(body);
        return res;
    }

    public static ComRes success() {
        return new ComRes();
    }

    public static ComRes fail() {
        ComRes comRes = new ComRes();
        comRes.setStatus(500);
        comRes.setMsg("ERROR");
        return comRes;
    }

    public static ComRes fail(String msg) {
        ComRes comRes = new ComRes();
        comRes.setStatus(500);
        comRes.setMsg(msg);
        return comRes;
    }

}