package org.aion.db.impl.monogodb;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import org.aion.base.util.ByteArrayWrapper;
import org.aion.db.impl.AbstractDB;

public class MongoDB extends AbstractDB {

  private String mongoClientUri;

  public MongoDB(String dbName, String mongoClientUri) {
    super(dbName);
    this.mongoClientUri = mongoClientUri;
  }

  @Override
  public boolean commitCache(Map<ByteArrayWrapper, byte[]> cache) {
    return false;
  }

  @Override
  protected byte[] getInternal(byte[] k) {
    return new byte[0];
  }

  @Override
  public boolean open() {
    return false;
  }

  @Override
  public boolean isOpen() {
    return false;
  }

  @Override
  public boolean isCreatedOnDisk() {
    return false;
  }

  @Override
  public long approximateSize() {
    return 0;
  }

  @Override
  public boolean isEmpty() {
    return false;
  }

  @Override
  public Set<byte[]> keys() {
    return null;
  }

  @Override
  public void put(byte[] bytes, byte[] bytes2) {

  }

  @Override
  public void delete(byte[] bytes) {

  }

  @Override
  public void putBatch(Map<byte[], byte[]> inputMap) {

  }

  @Override
  public void putToBatch(byte[] key, byte[] value) {

  }

  @Override
  public void commitBatch() {

  }

  @Override
  public void deleteBatch(Collection<byte[]> keys) {

  }

  @Override
  public void close() {

  }
}
