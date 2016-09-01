package org.cache2k.core;

/*
 * #%L
 * cache2k core
 * %%
 * Copyright (C) 2000 - 2016 headissue GmbH, Munich
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */

import java.text.DecimalFormat;
import java.util.concurrent.ThreadPoolExecutor;

import static org.cache2k.core.util.Util.formatMillis;

/**
 * Stable interface to request information from the cache, the object
 * safes values that need a longer calculation time, other values are
 * requested directly.
 */
class CacheBaseInfo implements InternalCacheInfo {

  private CommonMetrics metrics;
  private StorageMetrics storageMetrics = StorageMetrics.DUMMY;
  private HeapCache heapCache;
  private InternalCache cache;
  private long size;
  private long infoCreatedTime;
  private int infoCreationDeltaMs;
  private long missCnt;
  private long storageMissCnt;
  private long hitCnt;
  private long correctedPutCnt;
  private CollisionInfo collisionInfo;
  private String extraStatistics;
  private IntegrityState integrityState;
  private long asyncLoadsStarted = 0;
  private long asyncLoadsInFlight = 0;
  private int loaderThreadsLimit = 0;
  private int loaderThreadsMaxActive = 0;
  private long totalLoadCnt;

  /*
   * Consistent copies from heap cache. for 32 bit machines the access
   * is not atomic. We copy the values while under big lock.
   */
  private long clearedTime;
  private long newEntryCnt;
  private long keyMutationCnt;
  private long removedCnt;
  private long clearRemovedCnt;
  private long clearCnt;
  private long expiredRemoveCnt;
  private long evictedCnt;
  private long maxSize;
  private int evictionRunningCnt;

  public CacheBaseInfo(HeapCache _heapCache, InternalCache _userCache, long now) {
    infoCreatedTime = now;
    cache = _userCache;
    heapCache = _heapCache;
    metrics = _heapCache.metrics;
    EvictionMetrics em = _heapCache.eviction.getMetrics();
    newEntryCnt = em.getNewEntryCount();
    expiredRemoveCnt = em.getExpiredRemovedCount();
    evictedCnt = em.getEvictedCount();
    maxSize = em.getMaxSize();
    clearedTime = _heapCache.clearedTime;
    keyMutationCnt = _heapCache.keyMutationCnt;
    removedCnt = em.getRemovedCount();
    clearRemovedCnt = _heapCache.clearRemovedCnt;
    clearCnt = _heapCache.clearCnt;
    evictionRunningCnt = em.getEvictionRunningCount();
    integrityState = _heapCache.getIntegrityState();
    storageMetrics = _userCache.getStorageMetrics();
    collisionInfo = new CollisionInfo();
    _heapCache.hash.calcHashCollisionInfo(collisionInfo);
    extraStatistics = em.getExtraStatistics();
    if (extraStatistics.startsWith(", ")) {
      extraStatistics = extraStatistics.substring(2);
    }
    size = heapCache.getLocalSize();
    missCnt = metrics.getLoadCount() + metrics.getReloadCount() + metrics.getPeekHitNotFreshCount() + metrics.getPeekMissCount();
    storageMissCnt = storageMetrics.getReadMissCount() + storageMetrics.getReadNonFreshCount();
    hitCnt = em.getHitCount();
    correctedPutCnt = metrics.getPutNewEntryCount() + metrics.getPutHitCount() + metrics.getPutNoReadHitCount();
    if (_heapCache.loaderExecutor instanceof ThreadPoolExecutor) {
      ThreadPoolExecutor ex = (ThreadPoolExecutor) _heapCache.loaderExecutor;
      asyncLoadsInFlight = ex.getActiveCount();
      asyncLoadsStarted = ex.getTaskCount();
      loaderThreadsLimit = ex.getCorePoolSize();
      loaderThreadsMaxActive = ex.getLargestPoolSize();
    }
    totalLoadCnt = metrics.getLoadCount() + metrics.getReloadCount() + metrics.getRefreshCount();
  }

  String percentString(double d) {
    String s = Double.toString(d);
    return (s.length() > 5 ? s.substring(0, 5) : s) + "%";
  }

  public void setInfoCreationDeltaMs(int _millis) {
    infoCreationDeltaMs = _millis;
  }

  @Override
  public String getName() { return heapCache.name; }
  @Override
  public String getImplementation() { return cache.getClass().getSimpleName(); }

  @Override
  public long getReloadCount() {
    return metrics.getReloadCount();
  }

  @Override
  public long getSize() { return size; }
  @Override
  public long getHeapCapacity() { return maxSize; }
  @Override
  public long getStorageHitCnt() { return storageMetrics.getReadHitCount(); }

  @Override
  public long getStorageMissCnt() { return storageMissCnt; }
  @Override
  public long getGetCount() {
    long _putHit = metrics.getPutNoReadHitCount();
    long _heapHitButNoRead = metrics.getHeapHitButNoReadCount();
    return
      hitCnt + metrics.getPeekMissCount()
      + metrics.getLoadCount() - _putHit - _heapHitButNoRead;
  }
  @Override
  public long getMissCount() { return missCnt; }
  @Override
  public long getNewEntryCount() { return newEntryCnt; }
  @Override
  public long getHeapHitCount() { return hitCnt; }
  @Override
  public long getLoadCount() { return totalLoadCnt; }
  @Override
  public long getRefreshCount() { return metrics.getRefreshCount(); }
  @Override
  public long getInternalExceptionCount() { return metrics.getInternalExceptionCount(); }
  @Override
  public long getRefreshFailedCount() { return metrics.getRefreshFailedCount(); }
  @Override
  public long getSuppressedExceptionCount() { return metrics.getSuppressedExceptionCount(); }
  @Override
  public long getLoadExceptionCount() { return metrics.getLoadExceptionCount() + metrics.getSuppressedExceptionCount(); }
  @Override
  public long getRefreshedHitCount() { return metrics.getRefreshedHitCount(); }
  @Override
  public long getExpiredCount() { return expiredRemoveCnt + metrics.getExpiredKeptCount(); }
  @Override
  public long getEvictedCount() { return evictedCnt; }
  @Override
  public int getEvictionRunningCount() { return evictionRunningCnt; }
  @Override
  public long getRemovedCount() { return removedCnt; }
  @Override
  public long getPutCount() { return correctedPutCnt; }

  @Override
  public long getGoneSpinCount() {
    return metrics.getGoneSpinCount();
  }

  @Override
  public long getKeyMutationCount() { return keyMutationCnt; }
  @Override
  public long getTimerEventCount() { return metrics.getTimerEventCount(); }
  @Override
  public double getHitRate() {
    long cnt = getGetCount();
    return cnt == 0 ? 0.0 : ((cnt - missCnt) * 100D / cnt);
  }
  @Override
  public String getHitRateString() { return percentString(getHitRate()); }

  /** How many items will be accessed with collision */
  @Override
  public int getCollisionPercentage() {
    return
      (int) ((size - collisionInfo.collisionCnt) * 100 / size);
  }
  /** 100 means each collision has its own slot */
  @Override
  public int getSlotsPercentage() {
    return collisionInfo.collisionSlotCnt * 100 / collisionInfo.collisionCnt;
  }
  @Override
  public int getHq0() {
    return Math.max(0, 105 - collisionInfo.longestCollisionSize * 5) ;
  }
  @Override
  public int getHq1() {
    final int _metricPercentageBase = 60;
    int m =
      getCollisionPercentage() * ( 100 - _metricPercentageBase) / 100 + _metricPercentageBase;
    m = Math.min(100, m);
    m = Math.max(0, m);
    return m;
  }
  @Override
  public int getHq2() {
    final int _metricPercentageBase = 80;
    int m =
      getSlotsPercentage() * ( 100 - _metricPercentageBase) / 100 + _metricPercentageBase;
    m = Math.min(100, m);
    m = Math.max(0, m);
    return m;
  }
  @Override
  public int getHashQualityInteger() {
    if (size == 0 || collisionInfo.collisionSlotCnt == 0) {
      return 100;
    }
    int _metric0 = getHq0();
    int _metric1 = getHq1();
    int _metric2 = getHq2();
    if (_metric1 < _metric0) {
      int v = _metric0;
      _metric0 = _metric1;
      _metric1 = v;
    }
    if (_metric2 < _metric0) {
      int v = _metric0;
      _metric0 = _metric2;
      _metric2 = v;
    }
    if (_metric2 < _metric1) {
      int v = _metric1;
      _metric1 = _metric2;
      _metric2 = v;
    }
    if (_metric0 <= 0) {
      return 0;
    }
    _metric0 = _metric0 + ((_metric1 - 50) * 5 / _metric0);
    _metric0 = _metric0 + ((_metric2 - 50) * 2 / _metric0);
    _metric0 = Math.max(0, _metric0);
    _metric0 = Math.min(100, _metric0);
    return _metric0;
  }
  @Override
  public double getMillisPerLoad() { return getLoadCount() == 0 ? 0 : (metrics.getLoadMillis() * 1D / getLoadCount()); }
  @Override
  public long getLoadMillis() { return metrics.getLoadMillis(); }
  @Override
  public int getCollisionCount() { return collisionInfo.collisionCnt; }
  @Override
  public int getCollisionSlotCount() { return collisionInfo.collisionSlotCnt; }
  @Override
  public int getLongestSlot() { return collisionInfo.longestCollisionSize; }
  @Override
  public String getIntegrityDescriptor() { return integrityState.getStateDescriptor(); }
  @Override
  public long getStartedTime() { return heapCache.startedTime; }
  @Override
  public long getClearedTime() { return clearedTime; }
  @Override
  public long getInfoCreatedTime() { return infoCreatedTime; }
  @Override
  public int getInfoCreationDeltaMs() { return infoCreationDeltaMs; }
  @Override
  public int getHealth() {
    if (getHashQualityInteger() < 30 ||
      getKeyMutationCount() > 0 ||
      getInternalExceptionCount() > 0) {
      return 1;
    }
    return 0;
  }

  @Override
  public long getAsyncLoadsStarted() {
    return asyncLoadsStarted;
  }

  @Override
  public long getAsyncLoadsInFlight() {
    return asyncLoadsInFlight;
  }

  @Override
  public int getLoaderThreadsLimit() {
    return loaderThreadsLimit;
  }

  @Override
  public int getLoaderThreadsMaxActive() {
    return loaderThreadsMaxActive;
  }

  @Override
  public String getExtraStatistics() {
    return extraStatistics;
  }

  private static String timestampToString(long t) {
    if (t == 0) {
      return "-";
    }
    return formatMillis(t);
  }

  @Override
  public long getClearCount() {
    return clearCnt;
  }

  public long getClearRemovedCount() {
    return clearRemovedCnt;
  }

  public String toString() {
    return "Cache{" + heapCache.name + "}("
            + "size=" + getSize() + ", "
            + "capacity=" + (getHeapCapacity() != Long.MAX_VALUE ? getHeapCapacity() : "unlimited") + ", "
            + "get=" + getGetCount() + ", "
            + "miss=" + getMissCount() + ", "
            + "put=" + getPutCount() + ", "
            + "peekMiss=" + metrics.getPeekMissCount() + ", "
            + "peekHitNotFresh=" + metrics.getPeekHitNotFreshCount() + ", "
            + "load=" + getLoadCount() + ", "
            + "reload=" + getReloadCount() + ", "
            + "heapHit=" + getHeapHitCount() + ", "
            + "refresh=" + getRefreshCount() + ", "
            + "refreshSubmitFailed=" + getRefreshFailedCount() + ", "
            + "refreshedHit=" + getRefreshedHitCount() + ", "
            + "loadException=" + getLoadExceptionCount() + ", "
            + "suppressedException=" + getSuppressedExceptionCount() + ", "
            + "new=" + getNewEntryCount() + ", "
            + "expire=" + getExpiredCount() + ", "
            + "remove=" + getRemovedCount() + ", "
            + "clear=" + getClearCount() + ", "
            + "removeByClear=" + getClearRemovedCount() + ", "
            + "evict=" + getEvictedCount() + ", "
            + "timer=" + getTimerEventCount() + ", "
            + "goneSpin=" + getGoneSpinCount() + ", "
            + "hitRate=" + getHitRateString() + ", "
            + "msecs/load=" + formatMillisPerLoad(getMillisPerLoad())  + ", "
            + "asyncLoadsStarted=" + asyncLoadsStarted + ", "
            + "asyncLoadsInFlight=" + asyncLoadsInFlight + ", "
            + "loaderThreadsLimit=" + loaderThreadsLimit + ", "
            + "loaderThreadsMaxActive=" + loaderThreadsMaxActive + ", "
            + "created=" + timestampToString(getStartedTime()) + ", "
            + "cleared=" + timestampToString(getClearedTime()) + ", "
            + "infoCreated=" + timestampToString(getInfoCreatedTime()) + ", "
            + "infoCreationDeltaMs=" + getInfoCreationDeltaMs() + ", "
            + "collisions=" + getCollisionCount() + ", "
            + "collisionSlots=" + getCollisionSlotCount() + ", "
            + "longestSlot=" + getLongestSlot() + ", "
            + "hashQuality=" + getHashQualityInteger() + ", "
            + "impl=" + getImplementation() + ", "
            + getExtraStatistics() + ", "
            + "evictionRunning=" + getEvictionRunningCount() + ", "
            + "keyMutation=" + getKeyMutationCount() + ", "
            + "internalException=" + getInternalExceptionCount() + ", "
            + "integrityState=" + getIntegrityDescriptor()
      + ")";
  }

  static String formatMillisPerLoad(double val) {
    if (val < 0) {
      return "-";
    }
    DecimalFormat f = new DecimalFormat(".###");
    return f.format(val);
  }

}
