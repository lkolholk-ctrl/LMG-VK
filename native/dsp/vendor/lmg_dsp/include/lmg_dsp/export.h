#ifndef LMG_DSP_EXPORT_H
#define LMG_DSP_EXPORT_H
#if defined(_WIN32) && defined(LMG_DSP_SHARED)
# if defined(LMG_DSP_BUILDING)
#  define LMG_DSP_API __declspec(dllexport)
# else
#  define LMG_DSP_API __declspec(dllimport)
# endif
#elif defined(__GNUC__) && defined(LMG_DSP_SHARED)
# define LMG_DSP_API __attribute__((visibility("default")))
#else
# define LMG_DSP_API
#endif
#endif
