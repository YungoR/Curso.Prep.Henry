package ar.terracity.iurixmonitor;

import android.app.job.JobParameters;
import android.app.job.JobService;

public class IurixJobService extends JobService {
    @Override
    public boolean onStartJob(JobParameters params) {
        new Thread(() -> {
            try {
                MonitorEngine.run(getApplicationContext(), true);
            } finally {
                jobFinished(params, false);
            }
        }, "IURIX-Monitor").start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return true;
    }
}
