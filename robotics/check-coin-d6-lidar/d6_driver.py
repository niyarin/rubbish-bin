#!/usr/bin/env python3
# -*- coding: utf-8 -*-
from typing import List, Optional, Callable

from d6_protocol import D6Protocol, LidarPoint
from serial_port import SerialPort


class D6Lidar:
    BAUDRATE = 230400

    def __init__(self):
        self.serial = SerialPort(baudrate=self.BAUDRATE)
        self.protocol = D6Protocol()
        self.running = False

        self.serial.set_data_callback(self.protocol.process_data)

    @staticmethod
    def list_ports() -> List[str]:
        return SerialPort.list_ports()

    def connect(self, port: str) -> bool:
        return self.serial.connect(port)

    def disconnect(self):
        self.stop_scanning()
        self.serial.disconnect()

    def is_connected(self) -> bool:
        return self.serial.is_connected()

    def start_scanning(self) -> bool:
        if not self.is_connected():
            return False

        self.serial.write(D6Protocol.CMD_START)
        self.running = self.serial.start_reading()
        return self.running

    def stop_scanning(self):
        self.running = False
        self.serial.write(D6Protocol.CMD_STOP)
        self.serial.stop_reading()

    def set_scan_callback(self, callback: Optional[Callable[[List[LidarPoint]], None]]):
        self.protocol.set_scan_callback(callback)
