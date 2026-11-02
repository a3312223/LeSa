package com.leshang.lesa.cap.sandbox

import com.leshang.lesa.core.workspace.NioFdSys
import com.leshang.lesa.core.workspace.WorkspaceFileGate

/** JVM 测试里的关口(按路径的近似;手机上是 AndroidFdSys.gate)。 */
internal val JVM_GATE = WorkspaceFileGate(NioFdSys)
