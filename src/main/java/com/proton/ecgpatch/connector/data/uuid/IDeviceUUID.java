package com.proton.ecgpatch.connector.data.uuid;

/**
 * Created by 王梦思 on 2017/8/7.
 */

public interface IDeviceUUID {
    /**
     * ECG心电数据服务uuid
     */
    String getServiceUUID();

    /**
     * 写的uuid
     */
    String getNotifyCharacter();

    /**
     * 读的uuid
     */
    String getWriteCharacter();

    /**
     * 设备信息服务uuid
     */
    String getDeviceInfoServiceUUID();

    /**
     * 硬件版本Character uuid
     */
    String getCharacterVersionUUID();

    /**
     * 电量uuid
     */
    String getCharacterBatteryUUID();

    /**
     * 序列号uuid
     */
    String getCharacterSearialUUID();
}
