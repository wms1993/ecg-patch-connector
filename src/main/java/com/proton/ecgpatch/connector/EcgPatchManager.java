package com.proton.ecgpatch.connector;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;

import com.proton.ecgpatch.connector.callback.DataListener;
import com.proton.ecgpatch.connector.data.parse.EcgPatchBleDataParse;
import com.proton.ecgpatch.connector.data.parse.IBleDataParse;
import com.proton.ecgpatch.connector.data.uuid.EcgPatchUUID;
import com.proton.ecgpatch.connector.data.uuid.IDeviceUUID;
import com.proton.ecgpatch.connector.utils.BleUtils;
import com.proton.encrypt.EncryptHelper;
import com.wms.ble.BleOperatorManager;
import com.wms.ble.bean.ScanResult;
import com.wms.ble.callback.OnConnectListener;
import com.wms.ble.callback.OnReadCharacterListener;
import com.wms.ble.callback.OnScanListener;
import com.wms.ble.callback.OnSubscribeListener;
import com.wms.ble.callback.OnWriteCharacterListener;
import com.wms.ble.operator.IBleOperator;
import com.wms.logger.Logger;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by 王梦思 on 2018/7/7.
 * ble设备管理器
 */
@SuppressLint("StaticFieldLeak")
public class EcgPatchManager {
    /**
     * 设备管理器
     */
    private static final Map<String, EcgPatchManager> mEcgPatchManager = new HashMap<>();
    private static Context mContext;
    /**
     * 服务
     */
    private String serviceUUID;
    /**
     * 订阅uuid
     */
    private String notifyCharacterUUID;
    /**
     * 写uuid
     */
    private String writeCharacterUUID;
    /**
     * 服务:设备信息服务
     */
    private String serviceDeviceInfo;
    /**
     * 特征:设备版本号（可读）
     */
    private String characterVersion;
    /**
     * 特征:序列号（可读）
     */
    private String characterSerial;
    private final IBleOperator mBleOperator;
    /**
     * 接受数据监听器
     */
    private DataListener mDataListener;
    /**
     * 连接监听器
     */
    private OnConnectListener mConnectListener;
    /**
     * 数据解析
     */
    private final IBleDataParse dataParse = new EcgPatchBleDataParse();
    /**
     * 设备uuid数据提供者
     */
    private final IDeviceUUID deviceUUID = new EcgPatchUUID();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final String macAddress;
    /**
     * 是否在接收心电数据
     */
    private boolean receivingEcgData;
    private float gainMultiple = 200;

    private EcgPatchManager(String macAddress) {
        mBleOperator = BleOperatorManager.getInstance();
        this.macAddress = macAddress;
        initUUID();
    }

    public static void init(Context context) {
        mContext = context.getApplicationContext();
        BleOperatorManager.init(mContext);
        //初始化日志
        Logger.newBuilder()
                .tag("ecg_patch")
                .showThreadInfo(false)
                .methodCount(1)
                .methodOffset(5)
                .context(mContext)
                .deleteOnLaunch(true)
                .isDebug(BuildConfig.DEBUG)
                .build();
    }

    public static EcgPatchManager getInstance(String macAddress) {
        macAddress = macAddress.toUpperCase();
        if (mContext == null) {
            throw new IllegalStateException("You should initialize EcgPatchManager before using,You can initialize in your Application class");
        }
        if (!mEcgPatchManager.containsKey(macAddress)) {
            mEcgPatchManager.put(macAddress, new EcgPatchManager(macAddress));
        }
        return mEcgPatchManager.get(macAddress);
    }

    /**
     * 扫描心电贴的设备
     */
    public static void scanDevice(final OnScanListener listener) {
        scanDevice(10000, listener);
    }

    /**
     * 扫描心电贴的设备
     */
    public static void scanDevice(int scanTime, final OnScanListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("you should set a scan listener,or you will not receive data");
        }
        BleOperatorManager.getInstance().scanDevice(new OnScanListener() {

            @Override
            public void onScanStart() {
                listener.onScanStart();
            }

            @Override
            public void onDeviceFound(ScanResult scanResult) {
                listener.onDeviceFound(scanResult);
            }

            @Override
            public void onScanCanceled() {
                listener.onScanCanceled();
            }

            @Override
            public void onScanStopped() {
                listener.onScanStopped();
            }
        }, scanTime, "ECG_Paste");
    }

    /**
     * 停止搜索
     */
    public static void stopScan() {
        BleOperatorManager.getInstance().stopScan();
    }

    /**
     * 通过mac地址连接
     */
    public void connectByMacAddress() {
        clear(false);
        mBleOperator.setConnectListener(mConnectListener);
        scanConnect();
    }

    /**
     * 连接设备带回调
     */
    public void connectByMacAddress(OnConnectListener onConnectListener) {
        this.mConnectListener = onConnectListener;
        connectByMacAddress();
    }

    /**
     * 心电卡连接操作
     */
    public void connectEcgPatch(final OnConnectListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("connect listener can not be null");
        }
        if (mDataListener == null) {
            throw new IllegalArgumentException("you must set receiverDataLister before you connect" +
                    ",if you do not want receive data,you can use other connect method");
        }
        clear(false);
        this.mConnectListener = listener;
        mBleOperator.setConnectListener(new OnConnectListener() {
            @Override
            public void onConnectSuccess() {
                mConnectListener.onConnectSuccess();
                //设置mtu
                mBleOperator.setMTU(macAddress, 23);
                subscribeNotification();
                getBattery();
                getHardVersion();
                getSerial();
                writeGainCommand();
                writeStartEcgCommand();
                writeSyncTimeCommand();
            }

            @Override
            public void onConnectFaild() {
                if (mConnectListener != null) {
                    mConnectListener.onConnectFaild();
                }
            }

            @Override
            public void onDisconnect(boolean isManual) {
                if (mConnectListener != null) {
                    mConnectListener.onDisconnect(isManual);
                }
            }
        });

        scanConnect();
    }

    private void scanConnect() {
        scanDevice(15000, new OnScanListener() {

            private boolean hasFoundDevice;

            @Override
            public void onDeviceFound(ScanResult scanResult) {
                if (scanResult.getDevice().getAddress().equals(macAddress)) {
                    hasFoundDevice = true;
                    mBleOperator.connect(macAddress);
                    stopScan();
                }
            }

            @Override
            public void onScanStopped() {
                Logger.w("扫描停止");
                if (!hasFoundDevice) {
                    if (mConnectListener != null) {
                        mConnectListener.onConnectFaild();
                    }
                }
            }

            @Override
            public void onScanCanceled() {
                Logger.w("扫描取消");
                if (!hasFoundDevice) {
                    if (mConnectListener != null) {
                        mConnectListener.onConnectFaild();
                    }
                }
            }
        });
    }

    private void subscribeNotification() {
        mBleOperator.subscribeNotification(macAddress, serviceUUID, notifyCharacterUUID, new OnSubscribeListener() {
            @Override
            public void onNotify(String uuid, byte[] value) {
                if (value == null || value.length < 23) {
                    return;
                }
                byte[] data;
                if (value.length > 23) {
                    data = new byte[23];
                    System.arraycopy(value, 0, data, 0, data.length);
                } else {
                    data = value;
                }
                String dataHex = BleUtils.bytesToHexString(data);
                if (!TextUtils.isEmpty(dataHex)) {
                    if (dataHex.startsWith("aa030eff")) {
                        //电量内存
                        parseBatteryAndMemory(data);
                    } else if (dataHex.startsWith("aa040eff")) {
                        //心电数据
                        receivingEcgData = true;
                    } else if (dataHex.startsWith("aa110e")) {
                        parseGainMultiple(dataHex);
                    } else if (dataHex.startsWith("aa000eff")) {
                        Logger.w("心电贴时间同步成功");
                    } else {
                        if (receivingEcgData) {
                            parseEcgData(data);
                        }
                    }
                }
            }

            @Override
            public void onSuccess() {
                Logger.w("订阅成功");
            }

            @Override
            public void onFail() {
                Logger.w("订阅失败");
            }
        });
    }

    private void parseGainMultiple(String dataHex) {
        int num = Integer.parseInt(dataHex.substring(6, 8), 16);
        int decimals = Integer.parseInt(dataHex.substring(8, 10), 16);
        String value = String.valueOf(num);
        if (decimals < 10) {
            value = value + ".0" + decimals;
        } else {
            value = value + "." + decimals;
        }

        gainMultiple = Float.parseFloat(value);
        Logger.w("增益数据:" + gainMultiple);
    }

    private void writeSyncTimeCommand() {
        String time = Long.toHexString(System.currentTimeMillis());
        // 16eaa978fb2
        int size = 16 - time.length();
        StringBuilder zero = new StringBuilder();
        for (int i = 0; i < size; i++) {
            zero.append('0');
        }
        time = zero.toString() + time;

        byte[] bytes = BleUtils.hexStringToBytes(time);
        bytes = BleUtils.changeBytes(bytes);
        writeCommand("0008" + BleUtils.bytesToHexString(bytes).toUpperCase());
    }

    private void writeGainCommand() {
        writeCommand("1100");
    }

    private void writeStartEcgCommand() {
        writeCommand("04");
    }

    private void writeStopEcgCommand() {
        receivingEcgData = false;
        writeCommand("05");
    }

    private void writeCommand(final String command) {
        mBleOperator.write(macAddress, serviceUUID, writeCharacterUUID, BleUtils.hexStringToBytes(command), new OnWriteCharacterListener() {
            @Override
            public void onSuccess() {
                Logger.w("写入成功:", command);
            }

            @Override
            public void onFail() {
                Logger.w("写入失败:", command);
            }
        });
    }

    /**
     * 断开连接
     */
    public void disConnect() {
        disConnect(true);
    }

    /**
     * 断开连接
     */
    public void disConnect(boolean isClearListener) {
        clear(isClearListener);
        writeStopEcgCommand();
        mBleOperator.disConnect(macAddress);
        mEcgPatchManager.remove(macAddress);
    }

    /**
     * 清空信息
     */
    public void clear(boolean isClearListener) {
        receivingEcgData = false;
        if (isClearListener) {
            mConnectListener = null;
            mDataListener = null;
        }
    }

    /**
     * 解析电量
     */
    private void parseBatteryAndMemory(final byte[] data) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mDataListener != null) {
                    byte[] batteryData = new byte[1];
                    System.arraycopy(data, 4, batteryData, 0, 1);
                    Integer battery = dataParse.parseBattery(batteryData);
                    Logger.w("电量:", battery);
                    mDataListener.receiveBattery(battery);

                    byte[] memoryData = new byte[1];
                    System.arraycopy(data, 13, memoryData, 0, 1);
                    Integer memory = dataParse.parseMemory(memoryData);
                    Logger.w("内存:", memory);
                    mDataListener.receiveMemory(memory);
                }
            }
        });
    }

    private void parseHardVersion(final byte[] data) {
        String hardVersion = dataParse.parseHardVersion(data);
        Logger.w("固件版本:", hardVersion);
        if (mDataListener != null) {
            mDataListener.receiveHardVersion(hardVersion);
        }
    }

    private void parseSerial(final byte[] data) {
        String sn = dataParse.parseSerial(data);
        Logger.w("序列号:", sn);
        if (mDataListener != null) {
            mDataListener.receiveSerial(sn);
        }
    }

    /**
     * 解析ecg数据
     */
    private void parseEcgData(byte[] value) {
        //心电数据
        final byte[] ecgData = new byte[21];
        //包序
        final byte[] packageData = new byte[1];
        //状态
        byte[] status = new byte[1];
        System.arraycopy(value, 0, packageData, 0, packageData.length);
        System.arraycopy(value, 1, status, 0, status.length);
        System.arraycopy(value, 2, ecgData, 0, ecgData.length);

        final byte[] allStatus = BleUtils.getBooleanArray(status[0]);

        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (mDataListener != null) {
                    mDataListener.receivePackageNum(Integer.parseInt(BleUtils.bytesToHexString(packageData), 16));
                    mDataListener.receiveDataValidate(allStatus[0] == 1);
                    mDataListener.receiveFallDown(allStatus[1] == 0);
                    mDataListener.receiveFallOff(allStatus[2] == 0);
                    mDataListener.receiveSample(allStatus[3] == 0 ? 256 : 512);
                    mDataListener.receiveBluetoothData(EncryptHelper.encryptData(dataParse.parseEcgData(ecgData, gainMultiple)));
                }
            }
        });
    }

    /**
     * 获取电量
     */
    public EcgPatchManager getBattery() {
        writeCommand("03");
        return this;
    }

    /**
     * 获取固件版本
     */
    public EcgPatchManager getHardVersion() {
        mBleOperator.read(macAddress, serviceDeviceInfo, characterVersion, new OnReadCharacterListener() {
            @Override
            public void onSuccess(byte[] data) {
                parseHardVersion(data);
            }

            @Override
            public void onFail() {
                Logger.w("获取固件版本失败");
            }
        });
        return this;
    }

    /**
     * 获取序列号
     */
    public EcgPatchManager getSerial() {
        mBleOperator.read(macAddress, serviceDeviceInfo, characterSerial, new OnReadCharacterListener() {
            @Override
            public void onSuccess(byte[] data) {
                parseSerial(data);
            }

            @Override
            public void onFail() {
                Logger.w("获取序列号失败");
            }
        });
        return this;
    }

    /**
     * 初始化uuid
     */
    private void initUUID() {
        serviceUUID = deviceUUID.getServiceUUID();
        notifyCharacterUUID = deviceUUID.getNotifyCharacter();
        writeCharacterUUID = deviceUUID.getWriteCharacter();
        serviceDeviceInfo = deviceUUID.getDeviceInfoServiceUUID();
        characterSerial = deviceUUID.getCharacterSearialUUID();
        characterVersion = deviceUUID.getCharacterVersionUUID();
    }


    /**
     * 设置数据接受监听器，只能有一个监听器
     */
    public EcgPatchManager setDataListener(DataListener mReceiverDataListener) {
        this.mDataListener = mReceiverDataListener;
        return this;
    }

    public EcgPatchManager setConnectListener(OnConnectListener mConnectListener) {
        this.mConnectListener = mConnectListener;
        return this;
    }
}
