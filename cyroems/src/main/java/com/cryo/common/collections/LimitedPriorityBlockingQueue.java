package com.cryo.common.collections;

import com.cryo.common.error.FatalException;

import java.util.Comparator;
import java.util.concurrent.PriorityBlockingQueue;

/**
 * Limited Priority Queue with put/offer/take
 * 
 * @implNote poll(), offer(time) not implemented
 */
public class LimitedPriorityBlockingQueue<E> extends PriorityBlockingQueue<E>
{

    public LimitedPriorityBlockingQueue(final int maxDim)
    {
        super(maxDim);
        this.maxDim = maxDim;
        this.putSync = new Object();
    }
    /**
     * 
     * @param maxDim     initial and maximum size
     * @param comparator priority relation
     */
    public LimitedPriorityBlockingQueue(final int maxDim, final Comparator<E> comparator)
    {
        super(maxDim, comparator);
        this.maxDim = maxDim;
        this.putSync = new Object();
    }

    @Override
    public boolean offer(final E s)
    {
        synchronized (putSync)
        {
            if (size() >= maxDim)
            {
                // avoid growth
                return false;
            }
            else
            {
                return super.offer(s);
            }
        }
    }

    @Override
    public void put(final E s)
    {
        synchronized (putSync)
        {
            if (size() >= maxDim)
            {
                try
                {
                    putSync.wait();
                }
                catch (final InterruptedException e)
                {
                    // deal with empty inherited throw list
                    throw new FatalException(e.getMessage());
                }
            }
            super.put(s);
        }
    }

    @Override
    public synchronized E take() throws InterruptedException
    {
        final E res = super.take();
        synchronized (putSync)
        {
            putSync.notify();
        }
        return res;
    }

    protected int maxDim;
    protected final Object putSync;

    private static final long serialVersionUID = 1484945675769120529L;
}