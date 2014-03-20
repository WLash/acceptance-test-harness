package org.jenkinsci.test.acceptance.slave;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.jenkinsci.test.acceptance.SshKeyPair;
import org.jenkinsci.test.acceptance.machine.Machine;
import org.jenkinsci.test.acceptance.po.DumbSlave;
import org.jenkinsci.test.acceptance.po.Jenkins;
import org.jenkinsci.test.acceptance.po.Slave;
import org.jenkinsci.test.acceptance.po.SshPrivateKeyCredential;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Kohsuke Kawaguchi
 * @author Vivek Pandey
 */
public class SshSlaveController extends SlaveController {
    private final Machine machine;
    private final SshKeyPair keyPair;
    private final int slaveReadyTimeOutInSec;
    final AtomicBoolean slaveWaitComplete = new AtomicBoolean(false);

    @Inject
    public SshSlaveController(Machine machine, SshKeyPair keyPair, @Named("slaveReadyTimeOutInSec") int slaveReadyTimeOutInSec) {
        this.machine = machine;
        this.keyPair = keyPair;
        this.slaveReadyTimeOutInSec = slaveReadyTimeOutInSec;
    }

    @Override
    public Future<Slave> install(Jenkins j) {
        SshPrivateKeyCredential credential = new SshPrivateKeyCredential(j);

        try {
            credential.create("GLOBAL",machine.getUser(),keyPair.readPrivateKey());
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        final Slave s = create(machine.getPublicIpAddress(), j);

        //Slave is configured, now wait till its online
        return new Future<Slave>(){

            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                return false;
            }

            @Override
            public boolean isCancelled() {
                return slaveWaitComplete.get();
            }

            @Override
            public boolean isDone() {
                return slaveWaitComplete.get() || s.isOnline();
            }

            @Override
            public Slave get() throws InterruptedException, ExecutionException {
                waitForOnLineSlave(s, slaveReadyTimeOutInSec);
                return s;
            }

            @Override
            public Slave get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
                if(unit != TimeUnit.SECONDS){
                    timeout = unit.toSeconds(timeout);
                }
                waitForOnLineSlave(s, (int) timeout);
                return s;

            }
        };
    }

    @Override
    public void close() throws IOException {

    }

    private void waitForOnLineSlave(final Slave s, int timeout){
        logger.info(String.format("Wait for the new slave %s to come online in %s seconds",machine.getId(), timeout));
        try {
            long endTime = System.currentTimeMillis()+ TimeUnit.SECONDS.toMillis(timeout);
            while (System.currentTimeMillis()<endTime) {
                if(s.isOnline()){
                    slaveWaitComplete.set(true);
                    return;
                }
                sleep(1000);
            }
            throw new org.openqa.selenium.TimeoutException(String.format("Slave could not be online in %s seconds",timeout));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new Error(String.format("An exception occurred while waiting for slave to be online in %s seconds",timeout),e);
        }
    }

    private Slave create(String host, Jenkins j) {
        // Just to make sure the dumb slave is set up properly, we should seed it
        // with a FS root and executors
        final DumbSlave s = j.slaves.create(DumbSlave.class);

        s.find(by.input("_.host")).sendKeys(host);
        s.save();
        return s;

    }

    private static final Logger logger = LoggerFactory.getLogger(SshSlaveController.class);
}
