package io.fsq.hfile.reader.service

case class MsgAndSize(message: String, sizeInMB: Double)

trait HFileReader {
  def loadIntoMemory(processIsStarting: Boolean): MsgAndSize
  def close(evict: Boolean)
  def getEntries(): Long
  def getFirstKey(): Array[Byte]
  def getScanner: HFileScanner
  def getLastKey(): Array[Byte]
  def getName(): String
  def getTotalUncompressedBytes(): Long
  def getTrailerInfo(): String
  def indexSize: Long
  def length(): Long
  def loadFileInfo(): java.util.Map[Array[Byte],Array[Byte]]
  def isClosed(): Boolean
}
