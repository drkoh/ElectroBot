from PyQt6.QtCore import QRunnable, QObject, pyqtSignal, pyqtSlot, QThreadPool
from typing import Callable, Any

class WorkerSignals(QObject):
    started = pyqtSignal()
    finished = pyqtSignal()
    error = pyqtSignal(str)
    result = pyqtSignal(object)

class AsyncWorker(QRunnable):
    """
    Generic QRunnable worker to execute blocking network/IO tasks
    in the background thread pool without freezing PyQt6 UI.
    """
    def __init__(self, fn: Callable, *args, **kwargs):
        super().__init__()
        self.fn = fn
        self.args = args
        self.kwargs = kwargs
        self.signals = WorkerSignals()

    @pyqtSlot()
    def run(self):
        try:
            self.signals.started.emit()
            res = self.fn(*self.args, **self.kwargs)
            self.signals.result.emit(res)
        except Exception as e:
            self.signals.error.emit(str(e))
        finally:
            self.signals.finished.emit()

def run_async(fn: Callable, on_result: Callable[[Any], None] = None, on_error: Callable[[str], None] = None, on_finished: Callable[[], None] = None, *args, **kwargs) -> AsyncWorker:
    """Helper to quickly dispatch a function to global QThreadPool."""
    worker = AsyncWorker(fn, *args, **kwargs)
    if on_result:
        worker.signals.result.connect(on_result)
    if on_error:
        worker.signals.error.connect(on_error)
    if on_finished:
        worker.signals.finished.connect(on_finished)
        
    QThreadPool.globalInstance().start(worker)
    return worker
