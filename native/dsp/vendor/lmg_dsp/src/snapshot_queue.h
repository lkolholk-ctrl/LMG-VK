#ifndef LMG_DSP_SNAPSHOT_QUEUE_H
#define LMG_DSP_SNAPSHOT_QUEUE_H
#include <array>
#include <atomic>
#include <cstdint>
#include <type_traits>

namespace lmg { namespace dsp { namespace detail {

// One writer, one reader. N-1 usable slots. No overwrite of unread storage.
// No CAS/retries; popLatest reads at most N-1 snapshots captured at entry.
template<class T, std::uint32_t N = 8>
class SnapshotQueue final {
    static_assert(N >= 2, "At least two slots required");
    static_assert(std::is_trivially_copyable<T>::value, "Snapshots must be values");
    static_assert(std::atomic<std::uint32_t>::is_always_lock_free,
                  "This target lacks required lock-free 32-bit atomics");
public:
    bool push(const T& value) noexcept {
        const auto w = write_.load(std::memory_order_relaxed);
        const auto next = (w + 1) % N;
        if (next == read_.load(std::memory_order_acquire)) return false;
        slots_[w] = value;
        write_.store(next, std::memory_order_release);
        return true;
    }
    bool popLatest(T& destination) noexcept {
        auto r = read_.load(std::memory_order_relaxed);
        const auto w = write_.load(std::memory_order_acquire);
        if (r == w) return false;
        // A fixed captured endpoint also bounds work with a busy producer.
        do {
            destination = slots_[r];
            r = (r + 1) % N;
        } while (r != w);
        read_.store(r, std::memory_order_release);
        return true;
    }
private:
    std::array<T, N> slots_{};
    alignas(64) std::atomic<std::uint32_t> write_{0};
    alignas(64) std::atomic<std::uint32_t> read_{0};
};

}}}
#endif
