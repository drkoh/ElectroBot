from PyQt6.QtWidgets import QMenu, QLineEdit, QTableWidget, QApplication
from PyQt6.QtCore import Qt, QPoint
from PyQt6.QtGui import QAction, QClipboard

def attach_line_edit_context_menu(line_edit: QLineEdit, on_copied=None):
    """Adds a clean, native right-click context menu to a QLineEdit."""
    line_edit.setContextMenuPolicy(Qt.ContextMenuPolicy.CustomContextMenu)
    
    def show_menu(pos: QPoint):
        menu = QMenu(line_edit)
        
        act_cut = menu.addAction("✂️ Вирізати")
        act_cut.setShortcut("Ctrl+X")
        act_cut.setEnabled(line_edit.hasSelectedText() and not line_edit.isReadOnly())
        act_cut.triggered.connect(line_edit.cut)
        
        act_copy = menu.addAction("📋 Копіювати")
        act_copy.setShortcut("Ctrl+C")
        act_copy.setEnabled(line_edit.hasSelectedText())
        act_copy.triggered.connect(lambda: (line_edit.copy(), on_copied and on_copied("Текст скопійовано у буфер обміну")))
        
        act_paste = menu.addAction("📥 Вставити")
        act_paste.setShortcut("Ctrl+V")
        clipboard = QApplication.clipboard()
        act_paste.setEnabled(bool(clipboard.text()) and not line_edit.isReadOnly())
        act_paste.triggered.connect(line_edit.paste)
        
        menu.addSeparator()
        
        act_select_all = menu.addAction("🔲 Виділити все")
        act_select_all.setShortcut("Ctrl+A")
        act_select_all.setEnabled(bool(line_edit.text()))
        act_select_all.triggered.connect(line_edit.selectAll)
        
        act_clear = menu.addAction("✕ Очистити")
        act_clear.setEnabled(bool(line_edit.text()) and not line_edit.isReadOnly())
        act_clear.triggered.connect(line_edit.clear)
        
        menu.exec(line_edit.mapToGlobal(pos))
        
    line_edit.customContextMenuRequested.connect(show_menu)

def attach_table_context_menu(table: QTableWidget, on_select_row=None, on_save_row=None, on_copied=None):
    """Adds right-click context menu to a QTableWidget for copying and actions."""
    table.setContextMenuPolicy(Qt.ContextMenuPolicy.CustomContextMenu)
    
    def show_menu(pos: QPoint):
        item = table.itemAt(pos)
        if not item:
            return
            
        row = item.row()
        menu = QMenu(table)
        
        # 1. Copy Cell
        act_copy_cell = menu.addAction("📋 Копіювати виділену комірку")
        def copy_cell():
            QApplication.clipboard().setText(item.text())
            if on_copied:
                on_copied(f"Скопійовано: '{item.text()}'")
        act_copy_cell.triggered.connect(copy_cell)
        
        # 2. Copy Entire Row
        act_copy_row = menu.addAction("📑 Копіювати весь рядок")
        def copy_row():
            cols = [table.item(row, c).text() if table.item(row, c) else "" for c in range(table.columnCount())]
            row_str = " | ".join(cols)
            QApplication.clipboard().setText(row_str)
            if on_copied:
                on_copied("Рядок скопійовано у буфер")
        act_copy_row.triggered.connect(copy_row)
        
        menu.addSeparator()
        
        # 3. Actions if available
        if on_select_row:
            act_sel = menu.addAction("✅ Встановити як активну адресу")
            act_sel.triggered.connect(lambda: on_select_row(row))
            
        if on_save_row:
            act_fav = menu.addAction("⭐ Додати до збережених")
            act_fav.triggered.connect(lambda: on_save_row(row))
            
        menu.exec(table.mapToGlobal(pos))
        
    table.customContextMenuRequested.connect(show_menu)
