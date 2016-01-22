/*
 * Titan Robotics Framework Library
 * Copyright (c) 2015 Titan Robotics Club (http://www.titanrobotics.net)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package trclib;

import hallib.HalUtil;

/**
 * This class implements a platform independent PID controlled motor.
 * A PID controlled motor may consist of one or two physical motors,
 * a position sensor, typically an encoder (or could be a potentiometer).
 * Optionally, it supports a lower limit switch or even an upper limit
 * switch. In addition, it has stall protection support which will detect
 * motor stall condition and will cut power to the motor preventing it
 * from burning out.
 */
public class TrcPidMotor implements TrcTaskMgr.Task
{
    private static final String moduleName = "TrcPidMotor";
    private static final boolean debugEnabled = false;
    private TrcDbgTrace dbgTrace = null;

    private static final double MIN_MOTOR_POWER = -1.0;
    private static final double MAX_MOTOR_POWER = 1.0;

    private String instanceName;
    private TrcMotorController motor1;
    private TrcMotorController motor2;
    private TrcPidController pidCtrl;

    private double syncConstant = 0.0;
    private boolean active = false;
    private double positionScale = 1.0;
    private boolean holdTarget = false;
    private TrcEvent notifyEvent = null;
    private double expiredTime = 0.0;
    private double calPower = 0.0;
    private double motorPower = 0.0;
    private double prevPos = 0.0;
    private double prevTime = 0.0;
    private double prevTarget = 0.0;
    //
    // Stall protection.
    //
    private boolean stalled = false;
    private double stallMinPower = 0.0;
    private double stallTimeout = 0.0;
    private double resetTimeout = 0.0;

    /**
     * Constructor: Creates an instance of the object.
     *
     * @param instanceName specifies the instance name.
     * @param motor1 specifies motor1 object.
     * @param motor2 specifies motor2 object.
     *               If there is only one motor, this can be set to nul.
     * @param pidCtrl specifies the PID controller object.
     */
    public TrcPidMotor(
            final String instanceName,
            TrcMotorController motor1,
            TrcMotorController motor2,
            TrcPidController pidCtrl)
    {
        if (debugEnabled)
        {
            dbgTrace = new TrcDbgTrace(
                    moduleName + "." + instanceName,
                    false,
                    TrcDbgTrace.TraceLevel.API,
                    TrcDbgTrace.MsgLevel.INFO);
        }

        if (motor1 == null && motor2 == null)
        {
            throw new IllegalArgumentException("Must have at least one motor.");
        }

        if (pidCtrl == null)
        {
            throw new IllegalArgumentException("Must provide a PID controller.");
        }

        this.instanceName = instanceName;
        this.motor1 = motor1;
        this.motor2 = motor2;
        this.pidCtrl = pidCtrl;
    }   //TrcPidMotor

    /**
     * Constructor: Creates an instance of the object.
     *
     * @param instanceName specifies the instance name.
     * @param motor specifies motor object.
     * @param pidCtrl specifies the PID controller object.
     */
    public TrcPidMotor(
            final String instanceName, TrcMotorController motor, TrcPidController pidCtrl)
    {
        this(instanceName, motor, null, pidCtrl);
    }   //TrcPidMotor

    /**
     * This method returns the instance name.
     *
     * @return instance name.
     */
    public String toString()
    {
        return instanceName;
    }   //toString

    /**
     * This method sets the sync constants to synchronize the two motors. This is only a valid
     * call if there are two motors. It is ignored otherwise.
     *
     * @param syncConstant specifies the sync constant for synchronizing the two motors.
     */
    public void setSyncConstant(double syncConstant)
    {
        final String funcName = "setSyncConstant";

        if (debugEnabled)
        {
            dbgTrace.traceEnter(funcName, TrcDbgTrace.TraceLevel.API, "syncK=%f", syncConstant);
            dbgTrace.traceExit(funcName, TrcDbgTrace.TraceLevel.API);
        }

        if (motor1 != null && motor2 != null)
        {
            this.syncConstant = syncConstant;
        }
    }   //setSyncConstant

    /**
     * This method returns the state of the PID motor.
     *
     * @return true if PID motor is active, false otherwise.
     */
    public boolean isActive()
    {
        final String funcName = "isActive";

        if (debugEnabled)
        {
            dbgTrace.traceEnter(funcName, TrcDbgTrace.TraceLevel.API);
            dbgTrace.traceExit(
                    funcName, TrcDbgTrace.TraceLevel.API, "=%s", Boolean.toString(active));
        }

        return active;
    }   //isActive

    /**
     * This method cancels a previous active PID motor operation.
     */
    public void cancel()
    {
        final String funcName = "cancel";

        if (debugEnabled)
        {
            dbgTrace.traceEnter(funcName, TrcDbgTrace.TraceLevel.API);
        }

        if (active)
        {
            //
            // Stop the physical motor(s). If there is a notification event, singal it canceled.
            //
            stop(true);
            if (notifyEvent != null)
            {
                notifyEvent.cancel();
                notifyEvent = null;
            }
        }

        if (debugEnabled)
        {
            dbgTrace.traceExit(funcName, TrcDbgTrace.TraceLevel.API);
        }
    }   //cancel

    /**
     * This method sets the position scale. Instead of setting PID target with units
     * such as encoder count, one could set the scale to convert the unit to something
     * meaningful such as inches.
     *
     * @param positionScale specifies the position scale value.
     */
    public void setPositionScale(double positionScale)
    {
        final String funcName = "setPositionScale";

        if (debugEnabled)
        {
            dbgTrace.traceEnter(
                    funcName, TrcDbgTrace.TraceLevel.API,
                    "scale=%f", positionScale);
            dbgTrace.traceExit(funcName, TrcDbgTrace.TraceLevel.API);
        }

        this.positionScale = positionScale;
    }   //setTargetScale

    /**
     * This method sets stall protection. When stall protection is turned ON, it will
     * monitor the motor movement for stalled condition. A motor is considered stalled if:
     * - the power applied to the motor is above stallMinPower.
     * - the motor has not moved for at least stallTimeout.
     *
     * @param stallMinPower specifies the minimum motor power to detect stalled condition.
     *                      If the motor power is below stallMinPower, it won't consider it
     *                      as a stalled condition even if the motor does not move.
     * @param stallTimeout specifies the time in seconds that the motor must stopped before
     *                     it is declared stalled.
     * @param resetTimeout specifies the time in seconds the motor must be set to zero power
     *                     after it is declared stalled will the stalled condition be reset.
     *                     If this is set to zero, the stalled condition won't be cleared.
     */
    public void setStallProtection(double stallMinPower, double stallTimeout, double resetTimeout)
    {
        final String funcName = "setStallProtection";

        if (debugEnabled)
        {
            dbgTrace.traceEnter(
                    funcName, TrcDbgTrace.TraceLevel.API,
                    "stallMinPower=%f,stallTimeout=%f,resetTimeout=%f",
                    stallMinPower, stallTimeout, resetTimeout);
            dbgTrace.traceExit(funcName, TrcDbgTrace.TraceLevel.API);
        }

        this.stallMinPower = stallMinPower;
        this.stallTimeout = stallTimeout;
        this.resetTimeout = resetTimeout;
    }   //setStallProtection

    /**
     * This method starts a PID operation by setting the PID target.
     *
     * @param target specifies the PID target.
     * @param holdTarget specifies true to hold target after PID operation is completed.
     * @param event specifies an event object to signal when done.
     * @param timeout specifies a timeout value in seconds. If the operation is not completed
     *                without the specified timeout, the operation will be canceled and the
     *                event will be signaled. If no timeout is specified, it should be set to
     *                zero.
     */
    private void setTarget(double target, boolean holdTarget, TrcEvent event, double timeout)
    {
        final String funcName = "setTarget";

        if (debugEnabled)
        {
            dbgTrace.traceEnter(
                    funcName, TrcDbgTrace.TraceLevel.API,
                    "target=%f,hold=%s,event=%s,timeout=%f",
                    target, Boolean.toString(holdTarget),
                    event != null? event.toString(): "null", timeout);
        }

        if (active)
        {
            //
            // A previous PID operation in progress, stop it but don't stop the motor
            // to prevent jerkiness.
            //
            stop(false);
        }

        //
        // Set a new PID target.
        //
        pidCtrl.setTarget(target);

        //
        // If a notification event is provided, clear it.
        //
        if (event != null)
        {
            event.clear();
        }

        notifyEvent = event;
        expiredTime = timeout;
        this.holdTarget = holdTarget;
        //
        // If a timeout is provided, set the expired time.
        //
        if (timeout != 0.0)
        {
            expiredTime += HalUtil.getCurrentTime();
        }

        //
        // Set the PID motor task active.
        //
        setActive(true);

        if (debugEnabled)
        {
            dbgTrace.traceExit(funcName, TrcDbgTrace.TraceLevel.API);
        }
    }   //setTarget

    /**
     * This method starts a PID operation by setting the PID target.
     *
     * @param target specifies the PID target.
     * @param event specifies an event object to signal when done.
     * @param timeout specifies a timeout value in seconds. If the operation is not completed
     *                without the specified timeout, the operation will be canceled and the
     *                event will be signaled. If no timeout is specified, it should be set to
     *                zero.
     */
    public void setTarget(double target, TrcEvent event, double timeout)
    {
        setTarget(target, false, event, timeout);
    }   //setTarget

    /**
     * This method starts a PID operation by setting the PID target.
     *
     * @param target specifies the PID target.
     * @param holdTarget specifies true to hold target after PID operation is completed.
     */
    public void setTarget(double target, boolean holdTarget)
    {
        setTarget(target, holdTarget, null, 0.0);
    }   //setTarget

    /**
     * This method sets the PID motor power. It will check for the limit switches.
     * If activated, it won't allow the motor to go in that direction. It will also
     * check for stalled condition and cut motor power if stalled detected. It will
     * also check to reset the stalled condition if reset timeout was specified.
     *
     * @param power specifies the motor power.
     * @param rangeLow specifies the range low limit.
     * @param rangeHigh specifies the range high limit.
     * @param stopPid specifies true to stop previous PID operation, false otherwise.
     */
    private void setPower(double power, double rangeLow, double rangeHigh, boolean stopPid)
    {
        final String funcName = "setPower";

        if (debugEnabled)
        {
            dbgTrace.traceEnter(
                    funcName, TrcDbgTrace.TraceLevel.API,
                    "power=%f,rangeLow=%f,rangeHigh=%f,stopPid=%s",
                    power, rangeLow, rangeHigh, Boolean.toString(stopPid));
        }

        //
        // If the limit switch of the direction the motor is travelling is active,
        // don't allow the motor to move.
        //
        if (power > 0.0 && !motor1.isForwardLimitSwitchActive() ||
            power < 0.0 && !motor1.isReverseLimitSwitchActive())
        {
            if (active && stopPid)
            {
                //
                // A previous PID operation is still in progress, cancel it.
                // Don't stop the motor to prevent jerkiness.
                //
                stop(false);
            }

            power = TrcUtil.limit(power, rangeLow, rangeHigh);

            if (stalled)
            {
                if (power == 0.0)
                {
                    //
                    // We had a stalled condition but if power is removed for at least
                    // reset timeout, we clear the stalled condition.
                    //
                    if (resetTimeout == 0.0 ||
                        HalUtil.getCurrentTime() - prevTime > resetTimeout)
                    {
                        prevPos = motor1.getPosition();
                        prevTime = HalUtil.getCurrentTime();
                        stalled = false;
                    }
                }
                else
                {
                    prevTime = HalUtil.getCurrentTime();
                }
            }
            else
            {
                motorPower = power;
                if (stallMinPower > 0.0 && stallTimeout > 0.0)
                {
                    //
                    // Stall protection is ON, check for stall condition.
                    // - power is above stallMinPower
                    // - motor has not moved for at least stallTimeout.
                    //
                    double currPos = motor1.getPosition();
                    if (Math.abs(power) < Math.abs(stallMinPower) || currPos != prevPos)
                    {
                        prevPos = currPos;
                        prevTime = HalUtil.getCurrentTime();
                    }

                    if (HalUtil.getCurrentTime() - prevTime > stallTimeout)
                    {
                        //
                        // We have detected a stalled condition for at least
                        // stallTimeout. Kill power to protect the motor.
                        //
                        motorPower = 0.0;
                        stalled = true;
                    }
                }

                setMotorPower(motorPower);
            }
        }
        else
        {
            motorPower = 0.0;
            setMotorPower(motorPower);
        }

        if (debugEnabled)
        {
            dbgTrace.traceExit(funcName, TrcDbgTrace.TraceLevel.API);
        }
    }   //setPower

    /**
     * This method sets the PID motor power. It will check for the limit switches.
     * If activated, it won't allow the motor to go in that direction. It will also
     * check for stalled condition and cut motor power if stalled detected. It will
     * also check to reset the stalled condition if reset timeout was specified.
     *
     * @param power specifies the motor power.
     * @param rangeLow specifies the range low limit.
     * @param rangeHigh specifies the range high limit.
     */
    public void setPower(double power, double rangeLow, double rangeHigh)
    {
        setPower(power, rangeLow, rangeHigh, true);
    }   //setPower

    /**
     * This method sets the PID motor power. It will check for the limit switches.
     * If activated, it won't allow the motor to go in that direction. It will also
     * check for stalled condition and cut motor power if stalled detected. It will
     * also check to reset the stalled condition if reset timeout was specified.
     *
     * @param power specifies the motor power.
     */
    public void setPower(double power)
    {
        setPower(power, MIN_MOTOR_POWER, MAX_MOTOR_POWER, true);
    }   //setPower

    /**
     * This method sets the motor power with PID control. The motor will be under
     * PID control and the power specifies the upper bound of how fast the motor
     * will spin. The actual motor power is controlled by a PID controller with
     * the target either set to minPos or maxPos depending on the direction of
     * the motor. This is very useful in scenarios such as an elevator where you
     * want to have the elevator controlled by a joystick but would like PID
     * control to pay attention to the upper and lower limits and slow down when
     * approaching those limits. The joystick value will specify the upper bound
     * of the elevator speed. So if the joystick is only pushed half way, the
     * elevator will only go half power even though it is far away from the target.
     *
     * @param power specifies the upper bound power of the motor.
     * @param minPos specifies the minimum position of the motor travel.
     * @param maxPos specifies the maximum position of the motor travel.
     * @param holdTarget specifies true to hold target when power is set to 0, false otherwise.
     */
    public void setPidPower(double power, double minPos, double maxPos, boolean holdTarget)
    {
        final String funcName = "setPidPower";

        if (debugEnabled)
        {
            dbgTrace.traceEnter(
                    funcName, TrcDbgTrace.TraceLevel.API,
                    "power=%f,minPos=%f,maxPos=%f",
                    power, minPos, maxPos);
        }

        //
        // If one of the limit switches is active, don't allow the motor
        // to move in that direction. Reset the position sensor if the
        // lower limit switch is active.
        //
        if (motor1.isReverseLimitSwitchActive() && power < 0.0 ||
            motor1.isForwardLimitSwitchActive() && power > 0.0)
        {
            if (power < 0.0)
            {
                motor1.resetPosition();
            }
            power = 0.0;
        }

        //
        // If power is negative, set the target to minPos.
        // If power is positive, set the target to maxPos.
        // We only set a new target if the target has changed.
        // (i.e. either the motor changes direction, starting or stopping).
        //
        double currTarget = power < 0.0? minPos: power > 0.0? maxPos: 0.0;
        if (currTarget != prevTarget)
        {
            if (power == 0.0)
            {
                //
                // We are stopping, Relax the power range to max range so we have
                // full power to hold target if necessary.
                //
                pidCtrl.setOutputRange(MIN_MOTOR_POWER, MAX_MOTOR_POWER);
                if (holdTarget)
                {
                    //
                    // Hold target at current position.
                    //
                    setTarget(motor1.getPosition()*positionScale, true, null, 0.0);
                }
                else
                {
                    //
                    // We reached target and no holding target, we are done.
                    //
                    cancel();
                }
            }
            else
            {
                //
                // We changed direction, change the target.
                //
                power = Math.abs(power);
                pidCtrl.setOutputRange(-power, power);
                setTarget(currTarget, holdTarget, null, 0.0);
            }
            prevTarget = currTarget;
        }
        else if (power == 0.0)
        {
            //
            // We remain stopping, keep the power range relaxed in case we are holding
            // previous target.
            //
            pidCtrl.setOutputRange(MIN_MOTOR_POWER, MAX_MOTOR_POWER);
        }
        else
        {
            //
            // Direction did not change but we need to update the power range.
            //
            power = Math.abs(power);
            pidCtrl.setOutputRange(-power, power);
        }

        if (debugEnabled)
        {
            dbgTrace.traceExit(funcName, TrcDbgTrace.TraceLevel.API);
        }
    }   //setPidPower

    /**
     * This method returns the current scaled motor position.
     *
     * @return scaled motor position.
     */
    public double getPosition()
    {
        final String funcName = "zeroCalibrate";
        double value = motor1.getPosition()*positionScale;

        if (debugEnabled)
        {
            dbgTrace.traceEnter(funcName, TrcDbgTrace.TraceLevel.API);
            dbgTrace.traceExit(funcName, TrcDbgTrace.TraceLevel.API, "=%f", value);
        }

        return value;
    }   //getPosition

    /**
     * This method starts zero calibration mode by moving the motor with specified
     * calibration power until a limit switch is hit.
     *
     * @param calPower specifies calibration power. Generally, zero calibration
     *                 means this power value should be negative.
     */
    public void zeroCalibrate(double calPower)
    {
        final String funcName = "zeroCalibrate";

        if (debugEnabled)
        {
            dbgTrace.traceEnter(
                    funcName, TrcDbgTrace.TraceLevel.API,
                    "calPower=%f", calPower);
        }

        this.calPower = calPower;
        setActive(true);

        if (debugEnabled)
        {
            dbgTrace.traceExit(funcName, TrcDbgTrace.TraceLevel.API);
        }
    }   //zeroCalibrate

    /**
     * This method sets the motor power. If there are two motors, it will set both.
     *
     * @param power specifies the motor power.
     */
    private void setMotorPower(double power)
    {
        final String funcName = "setMotorPower";

        if (debugEnabled)
        {
            dbgTrace.traceEnter(
                    funcName, TrcDbgTrace.TraceLevel.API,
                    "power=%f", power);
        }

        double power1 = power;
        double power2 = power;
        if (syncConstant != 0.0)
        {
            double pos1 = motor1.getPosition();
            double pos2 = motor2.getPosition();
            double deltaPower = syncConstant*(pos1 - pos2);
            power1 -= deltaPower;
            if (power1 > 1.0)
            {
                power2 -= power1 - 1.0;
                power1 = 1.0;
            }
            else if (power1 < 1.0)
            {
                power2 -= power1 + 1.0;
                power1 = -1.0;
            }
        }
        motor1.setPower(power1);
        if (motor2 != null)
        {
            motor2.setPower(power2);
            /*
            if (motor2 instanceof CANJaguar)
            {
                CANJaguar.updateSyncGroup(syncGroup);
            }
            */
        }

        if (debugEnabled)
        {
            dbgTrace.traceExit(funcName, TrcDbgTrace.TraceLevel.API);
        }
    }   //setMotorPower

    /**
     * This method stops the PID motor. Stopping a PID motor consists of two things:
     * canceling PID and stopping the physical motor(s).
     *
     * @param stopMotor specifies true if also stopping the physical motor(s), false otherwise.
     */
    private void stop(boolean stopMotor)
    {
        final String funcName = "stop";

        if (debugEnabled)
        {
            dbgTrace.traceEnter(funcName, TrcDbgTrace.TraceLevel.FUNC,
                                "stopMotor=%s", Boolean.toString(stopMotor));
        }

        //
        // Canceling previous PID operation if any.
        //
        setActive(false);
        pidCtrl.reset();

        if (stopMotor)
        {
            setMotorPower(0.0);
        }

        motorPower = 0.0;
        calPower = 0.0;

        if (debugEnabled)
        {
            dbgTrace.traceExit(funcName, TrcDbgTrace.TraceLevel.FUNC);
        }
    }   //stop

    /**
     * This method activates/deactivates a PID motor operation by enabling/disabling
     * the PID motor task.
     *
     * @param active specifies true to activate a PID motor operation, false otherwise.
     */
    private void setActive(boolean active)
    {
        final String funcName = "setActive";

        if (debugEnabled)
        {
            dbgTrace.traceEnter(funcName, TrcDbgTrace.TraceLevel.FUNC,
                                "active=%s", Boolean.toString(active));
        }

        TrcTaskMgr taskMgr = TrcTaskMgr.getInstance();
        if (active)
        {
            taskMgr.registerTask(instanceName, this, TrcTaskMgr.TaskType.STOP_TASK);
            taskMgr.registerTask(instanceName, this, TrcTaskMgr.TaskType.POSTCONTINUOUS_TASK);
        }
        else
        {
            taskMgr.unregisterTask(this, TrcTaskMgr.TaskType.STOP_TASK);
            taskMgr.unregisterTask(this, TrcTaskMgr.TaskType.POSTCONTINUOUS_TASK);
        }
        this.active = active;

        if (debugEnabled)
        {
            dbgTrace.traceExit(funcName, TrcDbgTrace.TraceLevel.FUNC);
        }
    }   //setActive

    //
    // Implements TrcTaskMgr.Task
    //

    @Override
    public void startTask(TrcRobot.RunMode runMode)
    {
    }   //startTask

    /**
     * This method is called when the competition mode is about to end. It stops the
     * PID motor operation if any.
     *
     * @param runMode specifies the competition mode that is about to
     */
    @Override
    public void stopTask(TrcRobot.RunMode runMode)
    {
        final String funcName = "stopTask";

        if (debugEnabled)
        {
            dbgTrace.traceEnter(
                    funcName, TrcDbgTrace.TraceLevel.TASK,
                    "mode=%s", runMode.toString());
        }

        stop(true);

        if (debugEnabled)
        {
            dbgTrace.traceExit(funcName, TrcDbgTrace.TraceLevel.TASK);
        }
    }   //stopTask

    @Override
    public void prePeriodicTask(TrcRobot.RunMode runMode)
    {
    }   //prePeriodicTask

    /**
     * This method is called periodically to perform the PID motor task.
     * The PID motor task can be in one of two mode: zero calibration mode
     * and normal mode. In zero calibration mode, it will drive the motor
     * with the specified calibration power until it hits one of the limit
     * switches. Then it will stop the motor and reset the motor position
     * sensor. In normal mode, it calls the PID control to calculate the
     *
     *
     * @param runMode specifies the competition mode that is running.
     */
    @Override
    public void postPeriodicTask(TrcRobot.RunMode runMode)
    {
    }   //postPeriodicTask

    @Override
    public void preContinuousTask(TrcRobot.RunMode runMode)
    {
    }   //preContinuousTask

    @Override
    public void postContinuousTask(TrcRobot.RunMode runMode)
    {
        final String funcName = "postPeriodic";

        if (debugEnabled)
        {
            dbgTrace.traceEnter(
                    funcName, TrcDbgTrace.TraceLevel.TASK,
                    "mode=%s", runMode.toString());
        }

        if (calPower != 0.0)
        {
            //
            // We are in zero calibration mode.
            //
            if (calPower < 0.0 && !motor1.isReverseLimitSwitchActive() ||
                calPower > 0.0 && !motor1.isForwardLimitSwitchActive())
            {
                //
                // We are still calibrating and no limit switches are active yet.
                //
                setPower(calPower, MIN_MOTOR_POWER, MAX_MOTOR_POWER, false);
            }
            else
            {
                //
                // Done with zero calibration.
                //
                calPower = 0.0;
                setMotorPower(0.0);
                if (motor1.isReverseLimitSwitchActive())
                {
                    //
                    // Reset encoder only if lower limit switch is active.
                    //
                    motor1.resetPosition();
                }
                setActive(false);
            }
        }
        else
        {
            //
            // If we are not holding target and has rearched target or
            // we set a timeout and it has expired, we are done with the
            // operation. Stop the motor and if there is a notification
            // event, signal it.
            //
            if (!holdTarget && pidCtrl.isOnTarget() ||
                expiredTime != 0.0 && HalUtil.getCurrentTime() >= expiredTime)
            {
                stop(true);
                if (notifyEvent != null)
                {
                    notifyEvent.set(true);
                    notifyEvent = null;
                }
            }
            else
            {
                //
                // We are still in business. Call PID controller to calculate the
                // motor power and set it.
                //
                motorPower = pidCtrl.getOutput();
                setPower(motorPower, MIN_MOTOR_POWER, MAX_MOTOR_POWER, false);
            }
        }

        if (debugEnabled)
        {
            dbgTrace.traceExit(funcName, TrcDbgTrace.TraceLevel.TASK);
        }
    }   //postContinuousTask

}   //class TrcPidMotor
