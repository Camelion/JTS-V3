package ru.jts_dev.gameserver.parser.impl;

import org.antlr.v4.runtime.ANTLRInputStream;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.ParseTreeWalker;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import ru.jts_dev.gameserver.parser.ItemDatasBaseListener;
import ru.jts_dev.gameserver.parser.ItemDatasLexer;
import ru.jts_dev.gameserver.parser.ItemDatasParser;
import ru.jts_dev.gameserver.parser.data.item.SetData;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * @author Camelion
 * @since 07.01.16
 */
@Component
public class ItemDatasHolder extends ItemDatasBaseListener {
    private final Map<Integer, SetData> setsData = new HashMap<>();

    @Autowired
    private ApplicationContext context;

    public Map<Integer, SetData> getSetsData() {
        return Collections.unmodifiableMap(setsData);
    }

    @Override
    public void exitSet(ItemDatasParser.SetContext ctx) {
        int setId = Integer.valueOf(ctx.int_object().getText());
        int slotChest = Integer.valueOf(ctx.slot_chest().int_object().getText());
        SetData data = new SetData(setId, slotChest);

        if (ctx.slot_legs() != null)
            data.setSlotLegs(toList(ctx.slot_legs().int_array()));

        if (ctx.slot_head() != null)
            data.setSlotHead(toList(ctx.slot_head().int_array()));

        if (ctx.slot_gloves() != null)
            data.setSlotGloves(toList(ctx.slot_gloves().int_array()));

        if (ctx.slot_feet() != null)
            data.setSlotFeet(toList(ctx.slot_feet().int_array()));

        if (ctx.slot_lhand() != null)
            data.setSlotLhand(toList(ctx.slot_lhand().int_array()));

        data.setSlotAdditional(ctx.slot_additional().name_object().identifier_object().getText());
        data.setSetSkill(ctx.set_skill().name_object().identifier_object().getText());
        data.setSetEffectSkill(ctx.set_effect_skill().name_object().identifier_object().getText());
        data.setSetAdditionalEffectSkill(ctx.set_additional_effect_skill().name_object().identifier_object().getText());

        if (ctx.set_additional2_condition() != null)
            data.setSetAdditional2Condition(Integer.valueOf(ctx.set_additional2_condition().int_object().getText()));

        if (ctx.set_additional2_effect_skill() != null)
            data.setSetAdditional2EffectSkill(ctx.set_additional2_effect_skill().name_object().identifier_object().getText());

        data.setStrInc(fromInt2Array(ctx.str_inc().int_array2()));
        data.setConInc(fromInt2Array(ctx.con_inc().int_array2()));
        data.setDexInc(fromInt2Array(ctx.dex_inc().int_array2()));
        data.setIntInc(fromInt2Array(ctx.int_inc().int_array2()));
        data.setMenInc(fromInt2Array(ctx.men_inc().int_array2()));
        data.setWitInc(fromInt2Array(ctx.wit_inc().int_array2()));
        setsData.put(setId, data);
    }

    /**
     * Converts ANTLR Generated {@link ru.jts_dev.gameserver.parser.ItemDatasParser.Int_arrayContext}
     * to Java {@code List<Integer>} list
     *
     * @param ctx - parser {@link ru.jts_dev.gameserver.parser.ItemDatasParser.Int_arrayContext}
     * @return - filled {@code List<Integer>} list
     */
    private List<Integer> toList(ItemDatasParser.Int_arrayContext ctx) {
        List<Integer> list = new ArrayList<>(ctx.int_object().size());

        for (int i = 0; i < list.size(); i++) {
            list.add(Integer.valueOf(ctx.int_object(i).getText()));
        }

        return list;
    }

    /**
     * Converts ANTLR Generated {@link ru.jts_dev.gameserver.parser.ItemDatasParser.Int_array2Context}
     * to Java {@code int[]} array with two numbers
     *
     * @param ctx - parser {@link ru.jts_dev.gameserver.parser.ItemDatasParser.Int_array2Context}
     * @return - int[] array with two values
     */
    private int[] fromInt2Array(ItemDatasParser.Int_array2Context ctx) {
        if (ctx.int_object().size() != 2)
            throw new ArrayIndexOutOfBoundsException("size must be equal to 2");

        int[] array = new int[2];
        array[0] = Integer.valueOf(ctx.int_object(0).getText());
        array[1] = Integer.valueOf(ctx.int_object(1).getText());

        return array;
    }

    @PostConstruct
    public void parse() throws IOException {
        Resource file = context.getResource("scripts/itemdata.txt");
        try (InputStream is = file.getInputStream()) {
            ANTLRInputStream input = new ANTLRInputStream(is);
            ItemDatasLexer lexer = new ItemDatasLexer(input);
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            ItemDatasParser parser = new ItemDatasParser(tokens);

            ParseTree tree = parser.file();
            ParseTreeWalker walker = new ParseTreeWalker();
            walker.walk(this, tree);
        }
    }
}