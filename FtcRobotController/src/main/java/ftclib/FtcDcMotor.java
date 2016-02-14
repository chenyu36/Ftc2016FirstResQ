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

package ftclib;

import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.hardware.HardwareMap;

import hallib.HalMotorController;
import trclib.TrcDigitalInput;
import trclib.TrcDbgTrace;

/**
 * This class implements the Modern Robotics Motor Controller extending
 * TrcMotorController. It provides implementation of the abstract methods
 * in TrcMotorController. It supports limit switches. When this class is
 * constructed with limit switches, setPower will respect them and will
 * not move the motor into the direction where the limit switch is activated.
 * It also provides a software encoder reset without switching the Modern
 * Robotics motor controller mode which is problematic.
 */
public class FtcDcMotor implements HalMotorController
{
    private static final String moduleName = "FtcDcMotor";
    private static final boolean debugEnabled = false;
    private TrcDbgTrace dbgTrace = null;

    private String instanceName;
    private TrcDigitalInput reverseLimitSwitch = null;
    private TrcDigitalInput forwardLimitSwitch = null;
    private DcMotor motor;
    private int zeroEncoderValue;
    private int positionSensorSign = 1;
    private boolean brakeModeEnabled = true;

    /**
     * Constructor: Create an instance of the object.
     *
     * @param hardwareMap specifies the global hardware map.
     * @param instanceName specifies the instance name.
     * @param reverseLimitSwitch specifies the limit switch object for the reverse direction.
     * @param forwardLimitSwitch specifies the limit switch object for the forward direction.
     */
    public FtcDcMotor(
            HardwareMap hardwareMap,
            String instanceName,
            TrcDigitalInput reverseLimitSwitch,
            TrcDigitalInput forwardLimitSwitch)
    {
        this.instanceName = instanceName;

        if (debugEnabled)
        {
            dbgTrace = new TrcDbgTrace(
                    moduleName + "." + instanceName,
                    false,
                    TrcDbgTrace.TraceLevel.API,
                    TrcDbgTrace.MsgLevel.INFO);
        }

        this.reverseLimitSwitch = reverseLimitSwitch;
        this.forwardLimitSwitch = forwardLimitSwitch;
        motor = hardwareMap.dcMotor.get(instanceName);
        zeroEncoderValue = motor.getCurrentPosition();
    }   //FtcDcMotor

    /**
     * Constructor: Create an instance of the object.
     *
     * @param instanceName specifies the instance name.
     * @param reverseLimitSwitch specifies the limit switch object for the reverse direction.
     * @param forwardLimitSwitch specifies the limit switch object for the forward direction.
     */
    public FtcDcMotor(
            String instanceName,
            TrcDigitalInput reverseLimitSwitch,
            TrcDigitalInput forwardLimitSwitch)
    {
        this(FtcOpMode.getInstance().hardwareMap,
             instanceName,
             reverseLimitSwitch,
             forwardLimitSwitch);
    }   //FtcDcMotor

    /**
     * Constructor: Create an instance of the object.
     *
     * @param instanceName specifies the instance name.
     * @param reverseLimitSwitch specifies the limit switch object for the reverse direction.
     */
    public FtcDcMotor(
            String instanceName,
            TrcDigitalInput reverseLimitSwitch)
    {
        this(instanceName, reverseLimitSwitch, null);
    }   //FtcDcMotor

    /**
     * Constructor: Create an instance of the object.
     *
     * @param instanceName specifies the instance name.
     */
    public FtcDcMotor(String instanceName)
    {
        this(instanceName, null, null);
    }   //FtcDcMotor

    /**
     * This method returns the instance name.
     *
     * @return instance name.
     */
    public String toString()
    {
        return instanceName;
    }   //toString

    //
    // Implements HalMotorController interface.
    //

    /**
     * This method returns the motor position by reading the position sensor. The position
     * sensor can be an encoder or a potentiometer.
     *
     * @return current motor position.
     */
    @Override
    public double getPosition()
    {
        final String funcName = "getPosition";
        int position = positionSensorSign*(motor.getCurrentPosition() - zeroEncoderValue);

        if (debugEnabled)
        {
            dbgTrace.traceEnter(funcName, TrcDbgTrace.TraceLevel.API);
            dbgTrace.traceExit(funcName, TrcDbgTrace.TraceLevel.API, "=%d", position);
        }

        return (double)position;
    }   //getPosition

    /**
     * This method returns the speed of the motor rotation which is not
     * supported by the Modern Robotics motor controller.
     *
     * @throws UnsupportedOperationException exception.
     */
    @Override
    public double getSpeed()
    {
        final String funcName = "getSpeed";

        if (debugEnabled)
        {
            dbgTrace.traceEnter(funcName, TrcDbgTrace.TraceLevel.API);
            dbgTrace.traceExit(funcName, TrcDbgTrace.TraceLevel.API, "=0.0");
        }

        throw new UnsupportedOperationException(
                "Modern Robotics motor controllers do not have this support.");
    }   //getSpeed

    /**
     * This method returns the state of the forward limit switch.
     *
     * @return true if forward limit switch is closed, false otherwise.
     */
    @Override
    public boolean isFwdLimitSwitchClosed()
    {
        final String funcName = "isFwdLimitSwitchClosed";
        boolean isClosed = false;

        if (forwardLimitSwitch != null)
        {
            isClosed = forwardLimitSwitch.isActive();
        }

        if (debugEnabled)
        {
            dbgTrace.traceEnter(funcName, TrcDbgTrace.TraceLevel.API);
            dbgTrace.traceExit(funcName, TrcDbgTrace.TraceLevel.API,
                               "=%s", Boolean.toString(isClosed));
        }

        return isClosed;
    }   //isFwdLimitSwitchClosed

    /**
     * This method returns the state of the reverse limit switch.
     *
     * @return true if reverse limit switch is closed, false otherwise.
     */
    @Override
    public boolean isRevLimitSwitchClosed()
    {
        final String funcName = "isRevLimitSwitchClosed";
        boolean isClosed = false;

        if (reverseLimitSwitch != null)
        {
            isClosed = reverseLimitSwitch.isActive();
        }

        if (debugEnabled)
        {
            dbgTrace.traceEnter(funcName, TrcDbgTrace.TraceLevel.API);
            dbgTrace.traceExit(funcName, TrcDbgTrace.TraceLevel.API,
                               "=%s", Boolean.toString(isClosed));
        }

        return isClosed;
    }   //isRevLimitSwitchClosed

    /**
     * This method resets the motor position sensor, typically an encoder.
     */
    @Override
    public void resetPosition()
    {
        final String funcName = "resetPosition";

        if (debugEnabled)
        {
            dbgTrace.traceEnter(funcName, TrcDbgTrace.TraceLevel.API);
            dbgTrace.traceExit(funcName, TrcDbgTrace.TraceLevel.API);
        }

        //
        // Modern Robotics motor controllers supports resetting encoders
        // by setting the motor controller mode. This is a long operation
        // and has side effect of disabling the motor controller unless
        // you do another setMode to re-enable it. For example:
        //      motor.setMode(DcMotorController.RunMode.RESET_ENCODERS);
        //      motor.setMode(DcMotorController.RunMode.RUN_WITHOUT_ENCODERS);
        // It is a lot more efficient doing it in software.
        //
        zeroEncoderValue = motor.getCurrentPosition();
    }   //resetPosition

    /**
     * This method enables/disables motor brake mode. In motor brake mode, set power to 0 would
     * stop the motor very abruptly by shorting the motor wires together using the generated
     * back EMF to stop the motor. When brakMode is false (i.e. float/coast mode), the motor wires
     * are just disconnected from the motor controller so the motor will stop gradually.
     *
     * @param enabled specifies true to enable brake mode, false otherwise.
     */
    @Override
    public void setBrakeModeEnabled(boolean enabled)
    {
        final String funcName = "setBrakeModeEnabled";

        if (debugEnabled)
        {
            dbgTrace.traceEnter(funcName, TrcDbgTrace.TraceLevel.API,
                                "enabled=%s", Boolean.toString(enabled));
            dbgTrace.traceExit(funcName, TrcDbgTrace.TraceLevel.API);
        }

        this.brakeModeEnabled = enabled;
    }   //setBrakeModeEnabled

    /**
     * This method inverts the motor direction.
     *
     * @param inverted specifies true to invert motor direction, false otherwise.
     */
    @Override
    public void setInverted(boolean inverted)
    {
        final String funcName = "setInverted";

        if (debugEnabled)
        {
            dbgTrace.traceEnter(funcName, TrcDbgTrace.TraceLevel.API,
                                "inverted=%s", Boolean.toString(inverted));
            dbgTrace.traceExit(funcName, TrcDbgTrace.TraceLevel.API);
        }

        motor.setDirection(inverted? DcMotor.Direction.REVERSE: DcMotor.Direction.FORWARD);
    }   //setInverted

    /**
     * This method sets the output of the motor controller. Typically, the output is power.
     * However, some motor controllers are capable of other operating modes such as position,
     * speed, voltage, current, etc. When operating in those modes, output specifies the
     * appropriate value for that operating mode.
     *
     * @param output specifies the output for the motor controller. If the output is power, it
     *               is in the range of -1.0 to 1.0.
     */
    @Override
    public void setOutput(double output)
    {
        final String funcName = "setOutput";

        if (debugEnabled)
        {
            dbgTrace.traceEnter(funcName, TrcDbgTrace.TraceLevel.API, "output=%f", output);
            dbgTrace.traceExit(funcName, TrcDbgTrace.TraceLevel.API);
        }

        //
        // If we have limit switches, respect them.
        //
        if (output > 0.0 && forwardLimitSwitch != null && forwardLimitSwitch.isActive() ||
            output < 0.0 && reverseLimitSwitch != null && reverseLimitSwitch.isActive())
        {
            output = 0.0;
        }

        if (output != 0.0 || brakeModeEnabled)
        {
            motor.setPower(output);
        }
        else
        {
            motor.setPowerFloat();
        }
    }   //setOutput

    /**
     * This method inverts the position sensor direction. This may be rare but
     * there are scenarios where the motor encoder may be mounted somewhere in
     * the power train that it rotates opposite to the motor rotation. This will
     * cause the encoder reading to go down when the motor is receiving positive
     * power. This method can correct this situation.
     *
     * @param inverted specifies true to invert position sensor direction,
     *                 false otherwise.
     */
    @Override
    public void setPositionSensorInverted(boolean inverted)
    {
        final String funcName = "setPositionSensorInverted";

        if (debugEnabled)
        {
            dbgTrace.traceEnter(funcName, TrcDbgTrace.TraceLevel.API,
                                "inverted=%s", Boolean.toString(inverted));
            dbgTrace.traceExit(funcName, TrcDbgTrace.TraceLevel.API);
        }

        positionSensorSign = inverted? -1: 1;
    }   //setPositionSensorInverted

}   //class FtcDcMotor
