#!/usr/bin/env python3
# -*- coding: utf-8 -*-

import math
import threading
import tkinter as tk
from tkinter import ttk
from typing import List

import numpy as np
from matplotlib.backends.backend_tkagg import FigureCanvasTkAgg
from matplotlib.figure import Figure

from d6_driver import D6Lidar
from d6_protocol import LidarPoint


class LidarViewerApp:
    MAX_DISTANCE_MM = 2000  #  (mm)
    UPDATE_INTERVAL_MS = 50  # (ms)

    def __init__(self):
        self.root = tk.Tk()
        self.root.title("D6 LiDAR Viewer")
        self.root.geometry("900x700")

        self.lidar = D6Lidar()
        self.scan_data: List[LidarPoint] = []
        self.scan_lock = threading.Lock()

        self._setup_ui()
        self._setup_plot()

        self.lidar.set_scan_callback(self._on_scan_complete)
        self._update_plot()

    def _setup_ui(self):
        # コントロールフレーム
        control_frame = ttk.Frame(self.root, padding="5")
        control_frame.pack(fill=tk.X)

        # Port selector
        ttk.Label(control_frame, text="port:").pack(side=tk.LEFT)
        self.port_var = tk.StringVar()
        self.port_combo = ttk.Combobox(control_frame, textvariable=self.port_var, width=15)
        self.port_combo.pack(side=tk.LEFT, padx=5)

        # Update button
        ttk.Button(control_frame, text="Update", command=self._refresh_ports).pack(side=tk.LEFT)

        # Connect button
        self.connect_btn = ttk.Button(control_frame, text="Connect", command=self._toggle_connection)
        self.connect_btn.pack(side=tk.LEFT, padx=10)

        # start/stop button
        self.scan_btn = ttk.Button(control_frame, text="Start", command=self._toggle_scan, state=tk.DISABLED)
        self.scan_btn.pack(side=tk.LEFT)

        # status
        self.status_var = tk.StringVar(value="Disconnected")
        ttk.Label(control_frame, textvariable=self.status_var).pack(side=tk.RIGHT, padx=10)

        # stats
        stats_frame = ttk.Frame(self.root, padding="5")
        stats_frame.pack(fill=tk.X)

        self.stats_var = tk.StringVar(value="n-points: 0 | min distance: --- | max distance: ---")
        ttk.Label(stats_frame, textvariable=self.stats_var).pack(side=tk.LEFT)

        # 初回ポート一覧取得
        self._refresh_ports()

    def _setup_plot(self):
        self.fig = Figure(figsize=(8, 8), dpi=100)
        self.ax = self.fig.add_subplot(111, projection='polar')

        self.ax.set_theta_zero_location('N')
        self.ax.set_theta_direction(-1)
        self.ax.set_ylim(0, self.MAX_DISTANCE_MM)
        self.ax.set_title("D6 LiDAR plot")

        self.scatter = self.ax.scatter([], [], c=[], cmap='viridis', s=2, vmin=0, vmax=255)

        self.canvas = FigureCanvasTkAgg(self.fig, master=self.root)
        self.canvas.draw()
        self.canvas.get_tk_widget().pack(fill=tk.BOTH, expand=True)

    def _refresh_ports(self):
        ports = D6Lidar.list_ports()
        self.port_combo['values'] = ports
        if ports:
            self.port_combo.current(0)

    def _toggle_connection(self):
        if self.lidar.is_connected():
            # 切断
            self.lidar.disconnect()
            self.connect_btn.config(text="Connect")
            self.scan_btn.config(state=tk.DISABLED)
            self.status_var.set("Disconnected")
        else:
            # 接続
            port = self.port_var.get()
            if not port:
                self.status_var.set("Select port")
                return

            if self.lidar.connect(port):
                self.connect_btn.config(text="Disconnect")
                self.scan_btn.config(state=tk.NORMAL)
                self.status_var.set(f"Connected: {port}")
            else:
                self.status_var.set("Failed to connect port")

    def _toggle_scan(self):
        if self.lidar.running:
            self.lidar.stop_scanning()
            self.scan_btn.config(text="Start scanning")
            self.status_var.set("Stopped")
        else:
            if self.lidar.start_scanning():
                self.scan_btn.config(text="Stop scan")
                self.status_var.set("Scanning ...")
            else:
                self.status_var.set("Failed to start scan")

    def _on_scan_complete(self, points: List[LidarPoint]):
        with self.scan_lock:
            self.scan_data = points.copy()

    def _update_plot(self):
        with self.scan_lock:
            points = self.scan_data.copy()

        if points:
            angles = [math.radians(p.angle) for p in points]
            distances = [p.distance for p in points]
            intensities = [p.intensity for p in points]

            valid_data = [(a, d, i) for a, d, i in zip(angles, distances, intensities) if d > 0]

            if valid_data:
                angles, distances, intensities = zip(*valid_data)

                self.scatter.set_offsets(np.column_stack([angles, distances]))
                self.scatter.set_array(np.array(intensities))

                min_dist = min(distances)
                max_dist = max(distances)
                self.stats_var.set(f"n-points: {len(distances)} | min distance: {min_dist}mm | max distance: {max_dist}mm")

                self.canvas.draw_idle()

        self.root.after(self.UPDATE_INTERVAL_MS, self._update_plot)

    def run(self):
        self.root.protocol("WM_DELETE_WINDOW", self._on_close)
        self.root.mainloop()

    def _on_close(self):
        self.lidar.disconnect()
        self.root.destroy()


def main():
    app = LidarViewerApp()
    app.run()


if __name__ == "__main__":
    main()
