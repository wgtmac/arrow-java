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

@SuppressWarnings("unused")
public final class FixedSizeList extends Table {
  public static void ValidateVersion() { Constants.FLATBUFFERS_25_2_10(); }
  public static FixedSizeList getRootAsFixedSizeList(ByteBuffer _bb) { return getRootAsFixedSizeList(_bb, new FixedSizeList()); }
  public static FixedSizeList getRootAsFixedSizeList(ByteBuffer _bb, FixedSizeList obj) { _bb.order(ByteOrder.LITTLE_ENDIAN); return (obj.__assign(_bb.getInt(_bb.position()) + _bb.position(), _bb)); }
  public void __init(int _i, ByteBuffer _bb) { __reset(_i, _bb); }
  public FixedSizeList __assign(int _i, ByteBuffer _bb) { __init(_i, _bb); return this; }

  /**
   * Number of list items per value
   */
  public int listSize() { int o = __offset(4); return o != 0 ? bb.getInt(o + bb_pos) : 0; }

  public static int createFixedSizeList(FlatBufferBuilder builder,
      int listSize) {
    builder.startTable(1);
    FixedSizeList.addListSize(builder, listSize);
    return FixedSizeList.endFixedSizeList(builder);
  }

  public static void startFixedSizeList(FlatBufferBuilder builder) { builder.startTable(1); }
  public static void addListSize(FlatBufferBuilder builder, int listSize) { builder.addInt(0, listSize, 0); }
  public static int endFixedSizeList(FlatBufferBuilder builder) {
    int o = builder.endTable();
    return o;
  }

  public static final class Vector extends BaseVector {
    public Vector __assign(int _vector, int _element_size, ByteBuffer _bb) { __reset(_vector, _element_size, _bb); return this; }

    public FixedSizeList get(int j) { return get(new FixedSizeList(), j); }
    public FixedSizeList get(FixedSizeList obj, int j) {  return obj.__assign(__indirect(__element(j), bb), bb); }
  }
}
