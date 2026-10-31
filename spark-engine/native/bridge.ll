; ModuleID = "spark_node_jni"
target triple = "aarch64-linux-android"
target datalayout = ""

define i32 @"Java_app_local_spark_background_NodeEngine_start"(i8** %".1", i8* %".2", i8* %".3")
{
entry:
  %".5" = load i8*, i8** %".1"
  %".6" = bitcast i8* %".5" to i8**
  %".7" = getelementptr i8*, i8** %".6", i64 169
  %".8" = load i8*, i8** %".7"
  %".9" = bitcast i8* %".8" to i8* (i8**, i8*, i8*)*
  %".10" = getelementptr i8*, i8** %".6", i64 170
  %".11" = load i8*, i8** %".10"
  %".12" = bitcast i8* %".11" to void (i8**, i8*, i8*)*
  %".13" = call i8* %".9"(i8** %".1", i8* %".3", i8* null)
  %".14" = icmp eq i8* %".13", null
  br i1 %".14", label %"missing_string", label %"ready"
missing_string:
  ret i32 -2
ready:
  %".17" = call i64 @"strlen"(i8* %".13")
  %".18" = add i64 %".17", 1
  %".19" = add i64 %".18", 5
  %".20" = call i8* @"malloc"(i64 %".19")
  %".21" = icmp eq i8* %".20", null
  br i1 %".21", label %"allocation_failed", label %"allocated"
allocation_failed:
  call void %".12"(i8** %".1", i8* %".3", i8* %".13")
  ret i32 -3
allocated:
  %".25" = getelementptr [5 x i8], [5 x i8]* @"node_name", i32 0, i32 0
  %".26" = call i8* @"memcpy"(i8* %".20", i8* %".25", i64 5)
  %".27" = getelementptr i8, i8* %".20", i64 5
  %".28" = call i8* @"memcpy"(i8* %".27", i8* %".13", i64 %".18")
  %".29" = alloca i8*, i32 3
  %".30" = getelementptr i8*, i8** %".29", i64 0
  store i8* %".20", i8** %".30"
  %".32" = getelementptr i8*, i8** %".29", i64 1
  store i8* %".27", i8** %".32"
  %".34" = getelementptr i8*, i8** %".29", i64 2
  store i8* null, i8** %".34"
  %".36" = call i32 @"_ZN4node5StartEiPPc"(i32 2, i8** %".29")
  call void @"free"(i8* %".20")
  call void %".12"(i8** %".1", i8* %".3", i8* %".13")
  ret i32 %".36"
}

declare i64 @"strlen"(i8* %".1")

declare i8* @"malloc"(i64 %".1")

declare void @"free"(i8* %".1")

declare i8* @"memcpy"(i8* %".1", i8* %".2", i64 %".3")

@"node_name" = private constant [5 x i8] c"node\00"
declare i32 @"_ZN4node5StartEiPPc"(i32 %".1", i8** %".2")
