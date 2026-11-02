package com.leshang.lesa

import com.leshang.lesa.core.action.ActionExecutor
import com.leshang.lesa.core.action.ActionRecorder
import com.leshang.lesa.core.action.Origin
import com.leshang.lesa.core.action.execution
import com.leshang.lesa.core.guard.OperationGuards
import com.leshang.lesa.core.model.Part
import com.leshang.lesa.core.record.RunSink
import com.leshang.lesa.core.tool.ToolDispatcher
import com.leshang.lesa.core.tool.ToolExecution

/** 设备测试里直接做一个工具调用:和产品一样经执行入口(不配确认关卡,不等锁屏)。 */
suspend fun ToolDispatcher.runAction(call: Part.ToolCall): ToolExecution =
    ActionExecutor(this, OperationGuards(), null, gate = {}, recorder = ActionRecorder(RunSink.NONE)).run(call, Origin.Model(0)).execution()
