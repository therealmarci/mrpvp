package at.marcelr.mrpvp.snapshot;

import org.bukkit.inventory.ItemStack;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * MRtoolbar's belts as stored with a snapshot (column toolbar_belts). Format version 1:
 * <pre>
 *  byte  version (1)
 *  int   number of belts
 *  per belt: int hotbar slot (0-8), int selected tool (-1 = none), int length, bytes (ItemStack.serializeItemsAsBytes)
 * </pre>
 * Only recorded for the record - MRpvp never changes a belt.
 */
public final class ToolbarBelts {

    private static final int VERSION = 1;

    /** The belt behind one tool slot. */
    public record Belt(int slot, int selected, List<ItemStack> items) {
    }

    private ToolbarBelts() {
    }

    public static byte[] encode(List<Belt> belts) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(); DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeByte(VERSION);
            out.writeInt(belts.size());
            for (Belt belt : belts) {
                out.writeInt(belt.slot());
                out.writeInt(belt.selected());
                byte[] items = ItemStack.serializeItemsAsBytes(belt.items());
                out.writeInt(items.length);
                out.write(items);
            }
            out.flush();
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The belts, or null if there is no data (no MRtoolbar) or it is unreadable. */
    public static List<Belt> read(byte[] data) {
        if (data == null || data.length == 0) {
            return null;
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            if (in.readByte() != VERSION) {
                return null;
            }
            int count = in.readInt();
            List<Belt> belts = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int slot = in.readInt();
                int selected = in.readInt();
                byte[] items = new byte[in.readInt()];
                in.readFully(items);
                belts.add(new Belt(slot, selected, Arrays.asList(ItemStack.deserializeItemsFromBytes(items))));
            }
            return belts;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }
}
