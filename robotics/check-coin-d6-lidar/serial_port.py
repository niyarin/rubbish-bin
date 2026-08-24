#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
シリアルポート通信
"""

import serial
import serial.tools.list_ports
import threading
from typing import List, Optional, Callable


class SerialPort:
    """シリアルポート通信"""

    def __init__(self, baudrate: int = 230400):
        self.baudrate = baudrate
        self.serial: Optional[serial.Serial] = None
        self.running = False
        self.read_thread: Optional[threading.Thread] = None
        self.data_callback: Optional[Callable[[bytes], None]] = None

    @staticmethod
    def list_ports() -> List[str]:
        """利用可能なシリアルポートを列挙"""
        ports = serial.tools.list_ports.comports()
        return [port.device for port in ports]

    def set_data_callback(self, callback: Optional[Callable[[bytes], None]]):
        """データ受信コールバックを設定"""
        self.data_callback = callback

    def connect(self, port: str) -> bool:
        """ポートに接続"""
        try:
            self.serial = serial.Serial(
                port=port,
                baudrate=self.baudrate,
                bytesize=serial.EIGHTBITS,
                stopbits=serial.STOPBITS_ONE,
                parity=serial.PARITY_NONE,
                timeout=0.1
            )
            return True
        except serial.SerialException as e:
            print(f"接続エラー: {e}")
            return False

    def disconnect(self):
        """切断"""
        self.stop_reading()
        if self.serial and self.serial.is_open:
            self.serial.close()
        self.serial = None

    def is_connected(self) -> bool:
        """接続状態を確認"""
        return self.serial is not None and self.serial.is_open

    def write(self, data: bytes) -> bool:
        """データを送信"""
        if not self.is_connected():
            return False
        try:
            self.serial.write(data)
            return True
        except serial.SerialException:
            return False

    def start_reading(self) -> bool:
        """読み取りスレッドを開始"""
        if not self.is_connected():
            return False

        self.running = True
        self.read_thread = threading.Thread(target=self._read_loop, daemon=True)
        self.read_thread.start()
        return True

    def stop_reading(self):
        """読み取りスレッドを停止"""
        self.running = False
        if self.read_thread:
            self.read_thread.join(timeout=1.0)
            self.read_thread = None

    def _read_loop(self):
        """データ読み取りループ"""
        while self.running and self.serial and self.serial.is_open:
            try:
                data = self.serial.read(1024)
                if data and self.data_callback:
                    self.data_callback(data)
            except serial.SerialException:
                break
