package com.proton.ecgpatch.connector.callback;

/**
 * 数据接收监听器
 */
public abstract class DataListener {
    /**
     * 接收蓝牙原始数据
     *
     * @param data 加密后
     */
    public void receiveBluetoothData(byte[] data) {
    }

    /**
     * 接收包序号
     */
    public void receivePackageNum(int packageNum) {
    }

    /**
     * 接收数据校验
     */
    public void receiveDataValidate(boolean isValidate) {
    }

    /**
     * 接收是否跌倒
     */
    public void receiveFallDown(boolean isFallDown) {
    }

    /**
     * 接收导电脱落
     */
    public void receiveFallOff(boolean isFallOff) {
    }

    /**
     * 接收采样率
     */
    public void receiveSample(int sample) {
    }

    /**
     * 读取电量
     */
    public void receiveBattery(Integer battery) {
    }

    /**
     * 读取内存
     */
    public void receiveMemory(Integer memory) {
    }

    /**
     * 读取序列号
     */
    public void receiveSerial(String serial) {
    }

    /**
     * 读取硬件版本号
     */
    public void receiveHardVersion(String hardVersion) {
    }

}