from PyQt6.QtWidgets import QWidget, QToolTip
from PyQt6.QtGui import QPainter, QColor, QFont, QPen, QBrush, QLinearGradient
from PyQt6.QtCore import Qt, QRectF, QPointF, QDateTime, QTimer
from typing import List
import os, sys
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
from core.models import ScheduleInterval

class TimelineWidget(QWidget):
    def __init__(self, intervals: List[ScheduleInterval] = None, parent=None):
        super().__init__(parent)
        self.intervals = intervals or []
        self.setMinimumHeight(70)
        self.setMaximumHeight(85)
        self.setMouseTracking(True)
        
        # Repaint timer every 30s to update current time marker
        self.timer = QTimer(self)
        self.timer.timeout.connect(self.update)
        self.timer.start(30000)

    def set_intervals(self, intervals: List[ScheduleInterval]):
        self.intervals = intervals
        self.update()

    def paintEvent(self, event):
        painter = QPainter(self)
        painter.setRenderHint(QPainter.RenderHint.Antialiasing)
        
        w = self.width()
        h = self.height()
        
        margin_x = 16
        margin_top = 8
        bar_height = 28
        bar_width = w - 2 * margin_x
        
        if bar_width <= 10:
            return

        # 1. Background bar (Green by default: Power ON)
        # 1440 minutes in 24 hours
        bar_rect = QRectF(margin_x, margin_top, bar_width, bar_height)
        painter.setPen(Qt.PenStyle.NoPen)
        # Warm soft green for ON
        painter.setBrush(QColor("#238636"))
        painter.drawRoundedRect(bar_rect, 6, 6)
        
        # 2. Draw Outage intervals (Red/Coral)
        painter.setBrush(QColor("#da3633"))
        for inv in self.intervals:
            start_x = margin_x + (inv.start_minute / 1440.0) * bar_width
            end_x = margin_x + (inv.end_minute / 1440.0) * bar_width
            inv_w = max(2.0, end_x - start_x)
            inv_rect = QRectF(start_x, margin_top, inv_w, bar_height)
            painter.drawRect(inv_rect)
            
        # Draw border around bar
        pen_border = QPen(QColor("#353946"), 1)
        painter.setPen(pen_border)
        painter.setBrush(Qt.BrushStyle.NoBrush)
        painter.drawRoundedRect(bar_rect, 6, 6)

        # 3. Draw Hour tick marks and labels
        font = QFont("Segoe UI", 8)
        painter.setFont(font)
        painter.setPen(QColor("#9ba3b4"))
        
        hour_steps = [0, 2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24]
        for hour in hour_steps:
            x = margin_x + (hour / 24.0) * bar_width
            # tick
            painter.drawLine(QPointF(x, margin_top + bar_height), QPointF(x, margin_top + bar_height + 4))
            # label
            lbl = f"{hour:02d}:00" if hour < 24 else "24:00"
            text_rect = QRectF(x - 18, margin_top + bar_height + 6, 36, 16)
            painter.drawText(text_rect, Qt.AlignmentFlag.AlignCenter, lbl)

        # 4. Draw Current Time Marker Needle
        now = QDateTime.currentDateTime().time()
        current_mins = now.hour() * 60 + now.minute()
        cur_x = margin_x + (current_mins / 1440.0) * bar_width
        
        # Needle line
        pen_cur = QPen(QColor("#58a6ff"), 2)
        painter.setPen(pen_cur)
        painter.drawLine(QPointF(cur_x, margin_top - 4), QPointF(cur_x, margin_top + bar_height + 4))
        
        # Needle triangle head
        painter.setBrush(QColor("#58a6ff"))
        triangle = [
            QPointF(cur_x, margin_top),
            QPointF(cur_x - 4, margin_top - 6),
            QPointF(cur_x + 4, margin_top - 6)
        ]
        painter.drawPolygon(triangle)

    def mouseMoveEvent(self, event):
        margin_x = 16
        bar_width = self.width() - 2 * margin_x
        if bar_width <= 0:
            return
            
        x = event.position().x()
        if margin_x <= x <= margin_x + bar_width:
            minute_frac = (x - margin_x) / bar_width
            hover_min = int(minute_frac * 1440)
            hh = hover_min // 60
            mm = hover_min % 60
            
            # Check if outage at this minute
            is_outage = False
            for inv in self.intervals:
                if inv.start_minute <= hover_min < inv.end_minute:
                    is_outage = True
                    QToolTip.showText(event.globalPosition().toPoint(), 
                                      f"🔴 Відключення: {inv.start_str} - {inv.end_str}\n(Час курсора: {hh:02d}:{mm:02d})", self)
                    break
            if not is_outage:
                QToolTip.showText(event.globalPosition().toPoint(), 
                                  f"🟢 Світло є за графіком\n(Час курсора: {hh:02d}:{mm:02d})", self)
        else:
            QToolTip.hideText()
