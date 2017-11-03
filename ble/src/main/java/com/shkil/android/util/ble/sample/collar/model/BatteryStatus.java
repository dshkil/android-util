package com.shkil.android.util.ble.sample.collar.model;

import android.bluetooth.BluetoothGattCharacteristic;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.AnyThread;

import com.shkil.android.util.ble.util.io.CharacteristicReader;

import static android.bluetooth.BluetoothGattCharacteristic.FORMAT_UINT8;

@AnyThread
public class BatteryStatus implements Parcelable {

    public static CharacteristicReader<BatteryStatus> READER = new CharacteristicReader<BatteryStatus>() {
        @Override
        public BatteryStatus readValue(BluetoothGattCharacteristic characteristic) throws Exception {
            return BatteryStatus.parse(characteristic);
        }
    };

    private final int batteryLevel;
    private final boolean isCharging;

    public BatteryStatus(int batteryLevel, boolean isCharging) {
        this.batteryLevel = batteryLevel;
        this.isCharging = isCharging;
    }

    public int getBatteryLevel() {
        return batteryLevel;
    }

    public boolean isCharging() {
        return isCharging;
    }

    @Override
    public String toString() {
        return "BatteryStatus{" +
                "batteryLevel=" + batteryLevel +
                ", isCharging=" + isCharging +
                '}';
    }

    public String toShortString() {
        return "{" +
                "batteryLevel=" + batteryLevel +
                ", isCharging=" + isCharging +
                '}';
    }

    public static BatteryStatus parseDeviceValue(int batteryLevelValue) {
        int batteryLevel = batteryLevelValue & 0x7F;
        boolean isCharging = (batteryLevelValue & 0x80) != 0;
        return new BatteryStatus(batteryLevel, isCharging);
    }

    public static BatteryStatus parse(BluetoothGattCharacteristic characteristic) {
        Integer value = characteristic.getIntValue(FORMAT_UINT8, 0);
        if (value != null) {
            return parseDeviceValue(value);
        }
        throw new RuntimeException("Can't retrieve battery level");
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeInt(this.batteryLevel);
        out.writeByte(this.isCharging ? (byte) 1 : (byte) 0);
    }

    protected BatteryStatus(Parcel in) {
        this.batteryLevel = in.readInt();
        this.isCharging = in.readByte() != 0;
    }

    public static final Parcelable.Creator<BatteryStatus> CREATOR = new Parcelable.Creator<BatteryStatus>() {
        @Override
        public BatteryStatus createFromParcel(Parcel source) {
            return new BatteryStatus(source);
        }

        @Override
        public BatteryStatus[] newArray(int size) {
            return new BatteryStatus[size];
        }
    };
}
