#include <stdio.h>
#include <stdint.h>

#include "strings.h"

#include "unicode/ustdio.h"
#include "unicode/unum.h"


struct java_lang_Object;

void
method__Ojava_Dlang_DStandardError_Mwrite_Ascala_DInt_Rscala_DUnit(
    struct reference self, int32_t b)
{
  fputc(b, stderr);
}

void
method__Ojava_Dlang_DStandardError_Mflush_Rscala_DUnit(
    struct reference self)
{
  fflush(stderr);
}

void
method__Ojava_Dlang_DStandardOut_Mwrite_Ascala_DInt_Rscala_DUnit(
    struct reference self, int32_t b)
{
  fputc(b, stdout);
}

void
method__Ojava_Dlang_DStandardOut_Mflush_Rscala_DUnit(
    struct reference self)
{
  fflush(stdout);
}

UFILE* ustderr() {
  static UFILE *uout = NULL;
  if (uout == NULL) {
    uout = u_finit(stderr, NULL, NULL);
  }
  return uout;
}

UFILE* ustdout() {
  static UFILE *uout = NULL;
  if (uout == NULL) {
    uout = u_finit(stdout, NULL, NULL);
  }
  return uout;
}

void
method__Ojava_Dlang_DSystem_MdebugString_Ajava_Dlang_DString_Rscala_DUnit(struct reference self, struct reference sref)
{
  struct java_lang_String *s = (struct java_lang_String*)sref.object;
  u_file_write(s->s, s->len, ustderr());
}

void
method__Ojava_Dlang_DSystem_MdebugPointer_Ajava_Dlang_DObject_Rscala_DUnit(struct reference self, struct reference arg)
{
  fprintf(stderr, "%p\n", arg.object);
}

void
debugint(int32_t i)
{
  fprintf(stderr, "%d\n", i);
}
