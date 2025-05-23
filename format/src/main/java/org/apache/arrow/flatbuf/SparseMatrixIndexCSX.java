/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.arrow.flatbuf;

import com.google.flatbuffers.BaseVector;
import com.google.flatbuffers.BooleanVector;
import com.google.flatbuffers.ByteVector;
import com.google.flatbuffers.Constants;
import com.google.flatbuffers.DoubleVector;
import com.google.flatbuffers.FlatBufferBuilder;
import com.google.flatbuffers.FloatVector;
import com.google.flatbuffers.IntVector;
import com.google.flatbuffers.LongVector;
import com.google.flatbuffers.ShortVector;
import com.google.flatbuffers.StringVector;
import com.google.flatbuffers.Struct;
import com.google.flatbuffers.Table;
import com.google.flatbuffers.UnionVector;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Compressed Sparse format, that is matrix-specific.
 */
@SuppressWarnings("unused")
public final class SparseMatrixIndexCSX extends Table {
  public static void ValidateVersion() { Constants.FLATBUFFERS_25_2_10(); }
  public static SparseMatrixIndexCSX getRootAsSparseMatrixIndexCSX(ByteBuffer _bb) { return getRootAsSparseMatrixIndexCSX(_bb, new SparseMatrixIndexCSX()); }
  public static SparseMatrixIndexCSX getRootAsSparseMatrixIndexCSX(ByteBuffer _bb, SparseMatrixIndexCSX obj) { _bb.order(ByteOrder.LITTLE_ENDIAN); return (obj.__assign(_bb.getInt(_bb.position()) + _bb.position(), _bb)); }
  public void __init(int _i, ByteBuffer _bb) { __reset(_i, _bb); }
  public SparseMatrixIndexCSX __assign(int _i, ByteBuffer _bb) { __init(_i, _bb); return this; }

  /**
   * Which axis, row or column, is compressed
   */
  public short compressedAxis() { int o = __offset(4); return o != 0 ? bb.getShort(o + bb_pos) : 0; }
  /**
   * The type of values in indptrBuffer
   */
  public org.apache.arrow.flatbuf.Int indptrType() { return indptrType(new org.apache.arrow.flatbuf.Int()); }
  public org.apache.arrow.flatbuf.Int indptrType(org.apache.arrow.flatbuf.Int obj) { int o = __offset(6); return o != 0 ? obj.__assign(__indirect(o + bb_pos), bb) : null; }
  /**
   * indptrBuffer stores the location and size of indptr array that
   * represents the range of the rows.
   * The i-th row spans from `indptr[i]` to `indptr[i+1]` in the data.
   * The length of this array is 1 + (the number of rows), and the type
   * of index value is long.
   *
   * For example, let X be the following 6x4 matrix:
   * ```text
   *   X := [[0, 1, 2, 0],
   *         [0, 0, 3, 0],
   *         [0, 4, 0, 5],
   *         [0, 0, 0, 0],
   *         [6, 0, 7, 8],
   *         [0, 9, 0, 0]].
   * ```
   * The array of non-zero values in X is:
   * ```text
   *   values(X) = [1, 2, 3, 4, 5, 6, 7, 8, 9].
   * ```
   * And the indptr of X is:
   * ```text
   *   indptr(X) = [0, 2, 3, 5, 5, 8, 10].
   * ```
   */
  public org.apache.arrow.flatbuf.Buffer indptrBuffer() { return indptrBuffer(new org.apache.arrow.flatbuf.Buffer()); }
  public org.apache.arrow.flatbuf.Buffer indptrBuffer(org.apache.arrow.flatbuf.Buffer obj) { int o = __offset(8); return o != 0 ? obj.__assign(o + bb_pos, bb) : null; }
  /**
   * The type of values in indicesBuffer
   */
  public org.apache.arrow.flatbuf.Int indicesType() { return indicesType(new org.apache.arrow.flatbuf.Int()); }
  public org.apache.arrow.flatbuf.Int indicesType(org.apache.arrow.flatbuf.Int obj) { int o = __offset(10); return o != 0 ? obj.__assign(__indirect(o + bb_pos), bb) : null; }
  /**
   * indicesBuffer stores the location and size of the array that
   * contains the column indices of the corresponding non-zero values.
   * The type of index value is long.
   *
   * For example, the indices of the above X is:
   * ```text
   *   indices(X) = [1, 2, 2, 1, 3, 0, 2, 3, 1].
   * ```
   * Note that the indices are sorted in lexicographical order for each row.
   */
  public org.apache.arrow.flatbuf.Buffer indicesBuffer() { return indicesBuffer(new org.apache.arrow.flatbuf.Buffer()); }
  public org.apache.arrow.flatbuf.Buffer indicesBuffer(org.apache.arrow.flatbuf.Buffer obj) { int o = __offset(12); return o != 0 ? obj.__assign(o + bb_pos, bb) : null; }

  public static void startSparseMatrixIndexCSX(FlatBufferBuilder builder) { builder.startTable(5); }
  public static void addCompressedAxis(FlatBufferBuilder builder, short compressedAxis) { builder.addShort(0, compressedAxis, 0); }
  public static void addIndptrType(FlatBufferBuilder builder, int indptrTypeOffset) { builder.addOffset(1, indptrTypeOffset, 0); }
  public static void addIndptrBuffer(FlatBufferBuilder builder, int indptrBufferOffset) { builder.addStruct(2, indptrBufferOffset, 0); }
  public static void addIndicesType(FlatBufferBuilder builder, int indicesTypeOffset) { builder.addOffset(3, indicesTypeOffset, 0); }
  public static void addIndicesBuffer(FlatBufferBuilder builder, int indicesBufferOffset) { builder.addStruct(4, indicesBufferOffset, 0); }
  public static int endSparseMatrixIndexCSX(FlatBufferBuilder builder) {
    int o = builder.endTable();
    builder.required(o, 6);  // indptrType
    builder.required(o, 8);  // indptrBuffer
    builder.required(o, 10);  // indicesType
    builder.required(o, 12);  // indicesBuffer
    return o;
  }

  public static final class Vector extends BaseVector {
    public Vector __assign(int _vector, int _element_size, ByteBuffer _bb) { __reset(_vector, _element_size, _bb); return this; }

    public SparseMatrixIndexCSX get(int j) { return get(new SparseMatrixIndexCSX(), j); }
    public SparseMatrixIndexCSX get(SparseMatrixIndexCSX obj, int j) {  return obj.__assign(__indirect(__element(j), bb), bb); }
  }
}
