cmake_minimum_required(VERSION 3.16)
project(LmgDsp VERSION 1.0.0 LANGUAGES C CXX)

option(LMG_DSP_BUILD_TESTS "Build standalone host tests" OFF)
option(LMG_DSP_BUILD_EXAMPLES "Build PCM-only example (no audio device)" OFF)
# Standard BUILD_SHARED_LIBS chooses static (default) or shared library.
add_library(lmg_dsp src/filter.cpp src/dsp.cpp src/c_api.cpp)
add_library(LMG::dsp ALIAS lmg_dsp)
set_target_properties(lmg_dsp PROPERTIES POSITION_INDEPENDENT_CODE ON EXPORT_NAME dsp)
target_compile_features(lmg_dsp PUBLIC cxx_std_17)
target_include_directories(lmg_dsp PUBLIC
    $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/include>
    $<INSTALL_INTERFACE:include>)
target_compile_definitions(lmg_dsp PRIVATE LMG_DSP_BUILDING)
if(BUILD_SHARED_LIBS)
    target_compile_definitions(lmg_dsp PUBLIC LMG_DSP_SHARED)
endif()
if(MSVC)
    target_compile_options(lmg_dsp PRIVATE /W4 /fp:precise)
else()
    target_compile_options(lmg_dsp PRIVATE -Wall -Wextra -Wpedantic
        -fno-fast-math -ffp-contract=off)
endif()

if(LMG_DSP_BUILD_EXAMPLES)
    add_executable(lmg_dsp_pcm_example examples/pcm_example.cpp)
    target_link_libraries(lmg_dsp_pcm_example PRIVATE LMG::dsp)
endif()
if(LMG_DSP_BUILD_TESTS)
    enable_testing()
    find_package(Threads REQUIRED)
    add_executable(lmg_dsp_tests tests/test_dsp.cpp)
    target_include_directories(lmg_dsp_tests PRIVATE src)
    target_link_libraries(lmg_dsp_tests PRIVATE LMG::dsp Threads::Threads)
    if(NOT MSVC)
        target_compile_options(lmg_dsp_tests PRIVATE -Wall -Wextra -Wpedantic
            -fno-fast-math -ffp-contract=off)
    endif()
    add_test(NAME lmg_dsp_tests COMMAND lmg_dsp_tests)
    set_tests_properties(lmg_dsp_tests PROPERTIES TIMEOUT 180)
    add_executable(lmg_dsp_c_smoke tests/c_smoke.c)
    target_link_libraries(lmg_dsp_c_smoke PRIVATE LMG::dsp)
    set_target_properties(lmg_dsp_c_smoke PROPERTIES C_STANDARD 99 C_STANDARD_REQUIRED ON)
    add_test(NAME lmg_dsp_c_smoke COMMAND lmg_dsp_c_smoke)
endif()

include(GNUInstallDirs)
include(CMakePackageConfigHelpers)
install(TARGETS lmg_dsp EXPORT LmgDspTargets
    ARCHIVE DESTINATION ${CMAKE_INSTALL_LIBDIR}
    LIBRARY DESTINATION ${CMAKE_INSTALL_LIBDIR}
    RUNTIME DESTINATION ${CMAKE_INSTALL_BINDIR})
install(DIRECTORY include/ DESTINATION ${CMAKE_INSTALL_INCLUDEDIR})
install(EXPORT LmgDspTargets FILE LmgDspTargets.cmake NAMESPACE LMG::
    DESTINATION ${CMAKE_INSTALL_LIBDIR}/cmake/LmgDsp)
configure_package_config_file(cmake/LmgDspConfig.cmake.in
    ${CMAKE_CURRENT_BINARY_DIR}/LmgDspConfig.cmake
    INSTALL_DESTINATION ${CMAKE_INSTALL_LIBDIR}/cmake/LmgDsp)
write_basic_package_version_file(${CMAKE_CURRENT_BINARY_DIR}/LmgDspConfigVersion.cmake
    VERSION ${PROJECT_VERSION} COMPATIBILITY SameMajorVersion)
install(FILES ${CMAKE_CURRENT_BINARY_DIR}/LmgDspConfig.cmake
    ${CMAKE_CURRENT_BINARY_DIR}/LmgDspConfigVersion.cmake
    DESTINATION ${CMAKE_INSTALL_LIBDIR}/cmake/LmgDsp)
